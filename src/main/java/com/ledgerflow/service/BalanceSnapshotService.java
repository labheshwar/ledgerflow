package com.ledgerflow.service;

import com.ledgerflow.tenancy.TenantContext;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes balance checkpoints so that reading a balance does not mean summing
 * an account's entire history.
 *
 * Everything here is an optimization and nothing here is load bearing. A
 * balance read takes the newest snapshot at or before the date it cares
 * about and adds the entries since; with no snapshot at all it simply adds
 * every entry and gets the same answer, slower. That is deliberate -- it
 * means this job can be late, fail, be switched off, or have its rows
 * deleted and rebuilt, and no reader is ever wrong as a result. A design
 * where the snapshot were authoritative would turn every bug in this class
 * into a corrupted balance sheet.
 *
 * Snapshots are computed from the entries, never by adding to a previous
 * snapshot. Incremental maintenance is how stored balances drift in the
 * first place; recomputing from source means a wrong snapshot is repaired by
 * running the job again.
 */
@Service
public class BalanceSnapshotService {

    private static final Logger log = LoggerFactory.getLogger(BalanceSnapshotService.class);

    private final JdbcTemplate jdbcTemplate;

    public BalanceSnapshotService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Snapshots every account in the current organization as of the given
     * date, replacing any snapshot already held for that date.
     *
     * @return how many accounts were checkpointed
     */
    @Transactional
    public int snapshotAsOf(LocalDate asOfDate) {
        Long orgId = TenantContext.require();

        // ON CONFLICT makes re-running for a date idempotent, which is what
        // lets a failed run simply be repeated, and lets a period be
        // recomputed after a back-dated correction lands in it.
        int accounts = jdbcTemplate.update(
                """
                INSERT INTO account_balance_snapshots
                    (org_id, account_id, as_of_date, balance, base_balance, entry_count)
                SELECT
                    a.org_id,
                    a.id,
                    ?::date,
                    COALESCE(SUM(CASE WHEN qualifying.entry_type = 'DEBIT' THEN  qualifying.amount
                                      ELSE -qualifying.amount END), 0),
                    COALESCE(SUM(CASE WHEN qualifying.entry_type = 'DEBIT' THEN  qualifying.base_amount
                                      ELSE -qualifying.base_amount END), 0),
                    COUNT(qualifying.id)
                FROM accounts a
                LEFT JOIN (
                    SELECT e.id, e.account_id, e.entry_type, e.amount, e.base_amount
                    FROM entries e
                    JOIN transactions t ON t.id = e.transaction_id
                    WHERE t.txn_date <= ?::date
                ) qualifying ON qualifying.account_id = a.id
                GROUP BY a.org_id, a.id
                ON CONFLICT (account_id, as_of_date) DO UPDATE
                SET balance      = EXCLUDED.balance,
                    base_balance = EXCLUDED.base_balance,
                    entry_count  = EXCLUDED.entry_count,
                    created_at   = now()
                """,
                asOfDate,
                asOfDate);

        log.info("Checkpointed {} account balances for org {} as of {}", accounts, orgId, asOfDate);
        return accounts;
    }
}
