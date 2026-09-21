package com.ledgerflow.repository;

import com.ledgerflow.domain.AccountType;
import com.ledgerflow.domain.SystemAccountRole;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads accounts together with their derived balances.
 *
 * Written as explicit SQL rather than as Spring Data methods for two reasons.
 * The balance is a correlated lateral join that JPQL cannot express at all.
 * And the account list has to be *sortable* by that balance, which means the
 * ordering has to happen inside the same query -- there is no way to page a
 * result set by a value computed after the page has already been chosen.
 *
 * Sort columns come from a fixed map, never from caller input, so the only
 * strings ever concatenated into the SQL are ones written here. Everything
 * else is bound.
 *
 * Plain JDBC, not the EntityManager: it borrows the connection already bound
 * to the transaction, so row-level security applies exactly as it does to
 * every other read, while the row mapping stays explicit instead of relying
 * on Hibernate to guess types for a projection that is not an entity.
 */
@Repository
@Transactional(readOnly = true)
public class AccountBalanceQueries {

    /**
     * API sort name to SQL expression. An unlisted name never reaches the
     * query -- SortWhitelist rejects it at the edge with a 400, and this map
     * is the second gate.
     */
    private static final Map<String, String> SORTABLE = Map.of(
            "code", "a.code",
            "name", "a.name",
            "type", "a.type",
            "currency", "a.currency",
            "balance", BalanceQueries.BALANCE,
            "createdAt", "a.created_at",
            "updatedAt", "a.updated_at");

    private static final String SELECT_WITH_BALANCE =
            """
            SELECT a.id, a.code, a.name, a.description, a.type, a.currency,
                   a.parent_id, a.system_role, a.is_postable, a.archived_at,
                   a.created_at, a.updated_at,
                   %s AS balance,
                   %s AS base_balance
            FROM accounts a
            %s
            """
                    .formatted(BalanceQueries.BALANCE, BalanceQueries.BASE_BALANCE, BalanceQueries.DERIVED_BALANCE_JOINS);

    private static final RowMapper<AccountWithBalance> MAPPER = AccountBalanceQueries::mapRow;

    private final NamedParameterJdbcTemplate jdbc;

    public AccountBalanceQueries(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<AccountWithBalance> findById(Long accountId) {
        List<AccountWithBalance> found = jdbc.query(
                SELECT_WITH_BALANCE + " WHERE a.id = :id",
                new MapSqlParameterSource("id", accountId),
                MAPPER);
        return found.stream().findFirst();
    }

    public List<AccountWithBalance> findAllOrderedByName() {
        return jdbc.query(SELECT_WITH_BALANCE + " ORDER BY a.name ASC", MAPPER);
    }

    /** The whole chart in code order, which is the order a trial balance reads in. */
    public List<AccountWithBalance> findAllOrderedByCode() {
        return jdbc.query(SELECT_WITH_BALANCE + " ORDER BY a.code ASC", MAPPER);
    }

    /**
     * A blank term matches everything, so the caller never has to branch.
     * Note the empty-string normalization: binding a null straight into
     * LOWER() leaves Postgres unable to infer the parameter's type, and it
     * rejects the entire statement with "function lower(bytea) does not
     * exist" -- which fails every *unfiltered* request while filtered ones
     * pass, the least intuitive failure mode available.
     */
    public Page<AccountWithBalance> search(String q, AccountType type, Pageable pageable) {
        String term = q == null ? "" : q.trim();
        String typeName = type == null ? "" : type.name();

        String where =
                """
                WHERE (:type = '' OR a.type = :type)
                  AND (:q = '' OR LOWER(a.name) LIKE LOWER('%' || :q || '%'))
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("q", term)
                .addValue("type", typeName);

        Long total = jdbc.queryForObject(
                "SELECT count(*) FROM accounts a " + where, params, Long.class);

        List<AccountWithBalance> rows = jdbc.query(
                SELECT_WITH_BALANCE + where + orderBy(pageable.getSort()) + " LIMIT :limit OFFSET :offset",
                params.addValue("limit", pageable.getPageSize())
                        .addValue("offset", pageable.getOffset()),
                MAPPER);

        return new PageImpl<>(rows, pageable, total == null ? 0 : total);
    }

    /**
     * Every postable revenue and expense account with a non-zero balance as
     * of {@code asOfDate}, for the year-end close.
     *
     * A dedicated query rather than the shared "as of now" join, because
     * closing needs the answer as of a specific date -- typically the last
     * day of a fiscal year that has since ended -- not whatever the balance
     * happens to be today. The snapshot lookup is bounded by that same date
     * for the same reason: a snapshot taken after asOfDate would otherwise
     * be picked as the newest one and silently include entries the close is
     * not supposed to see yet.
     *
     * Headings are excluded by construction, not filtered out afterwards --
     * an account that cannot be posted to always has a balance of exactly
     * zero, which the "non-zero" filter already removes.
     */
    public List<AccountWithBalance> findPostableRevenueAndExpenseAsOf(LocalDate asOfDate) {
        String sql =
                """
                SELECT a.id, a.code, a.name, a.description, a.type, a.currency,
                       a.parent_id, a.system_role, a.is_postable, a.archived_at,
                       a.created_at, a.updated_at,
                       COALESCE(s.balance, 0) + COALESCE(d.delta, 0) AS balance,
                       COALESCE(s.base_balance, 0) + COALESCE(d.base_delta, 0) AS base_balance
                FROM accounts a
                LEFT JOIN LATERAL (
                    SELECT sn.as_of_date, sn.balance, sn.base_balance
                    FROM account_balance_snapshots sn
                    WHERE sn.account_id = a.id AND sn.as_of_date <= :asOfDate
                    ORDER BY sn.as_of_date DESC
                    LIMIT 1
                ) s ON TRUE
                LEFT JOIN LATERAL (
                    SELECT
                        SUM(CASE WHEN e.entry_type = 'DEBIT' THEN  e.amount ELSE -e.amount END) AS delta,
                        SUM(CASE WHEN e.entry_type = 'DEBIT' THEN  e.base_amount ELSE -e.base_amount END) AS base_delta
                    FROM entries e
                    JOIN transactions t ON t.id = e.transaction_id
                    WHERE e.account_id = a.id
                      AND t.txn_date <= :asOfDate
                      AND (s.as_of_date IS NULL OR t.txn_date > s.as_of_date)
                ) d ON TRUE
                WHERE a.type IN ('REVENUE', 'EXPENSE')
                  AND a.is_postable = TRUE
                  AND (COALESCE(s.base_balance, 0) + COALESCE(d.base_delta, 0)) <> 0
                """;
        return jdbc.query(sql, new MapSqlParameterSource("asOfDate", asOfDate), MAPPER);
    }

    public long count() {
        Long total = jdbc.queryForObject(
                "SELECT count(*) FROM accounts a", new MapSqlParameterSource(), Long.class);
        return total == null ? 0 : total;
    }

    /**
     * Every account's balance added up, in the reporting currency.
     *
     * Base amounts, not account-currency amounts: a total that adds dollars
     * to euros is not a number, it is a category error rendered with a
     * currency symbol in front of it.
     */
    public BigDecimal totalBaseBalance() {
        BigDecimal total = jdbc.queryForObject(
                "SELECT COALESCE(SUM(%s), 0) FROM accounts a %s"
                        .formatted(BalanceQueries.BASE_BALANCE, BalanceQueries.DERIVED_BALANCE_JOINS),
                new MapSqlParameterSource(),
                BigDecimal.class);
        return total == null ? BigDecimal.ZERO : total;
    }

    private String orderBy(Sort sort) {
        if (sort.isUnsorted()) {
            return " ORDER BY a.name ASC";
        }
        StringBuilder clause = new StringBuilder(" ORDER BY ");
        boolean first = true;
        for (Sort.Order order : sort) {
            String column = SORTABLE.get(order.getProperty());
            if (column == null) {
                continue;
            }
            if (!first) {
                clause.append(", ");
            }
            clause.append(column).append(order.isAscending() ? " ASC" : " DESC");
            first = false;
        }
        if (first) {
            return " ORDER BY a.name ASC";
        }
        // A stable tiebreak, so two accounts with equal balances do not swap
        // places between pages and make a row appear twice or not at all.
        return clause.append(", a.id ASC").toString();
    }

    private static AccountWithBalance mapRow(ResultSet rs, int rowNum) throws SQLException {
        String role = rs.getString("system_role");
        BigDecimal balance = rs.getBigDecimal("balance");
        return new AccountWithBalance(
                rs.getLong("id"),
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("description"),
                AccountType.valueOf(rs.getString("type")),
                rs.getString("currency"),
                (Long) rs.getObject("parent_id"),
                role == null ? null : SystemAccountRole.valueOf(role),
                rs.getBoolean("is_postable"),
                rs.getObject("archived_at", OffsetDateTime.class),
                balance,
                rs.getBigDecimal("base_balance"),
                balance,
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class));
    }
}
