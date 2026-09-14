package com.ledgerflow.outbox;

import com.zaxxer.hikari.HikariDataSource;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Privileged access to the outbox table, owned entirely by the worker.
 *
 * Holds its own small connection pool rather than sharing the application's.
 * Two reasons. The obvious one is identity: this pool connects as
 * ledgerflow_admin, which bypasses row-level security, because a poller that
 * could only see one organization's events would need a query per tenant
 * every half second. The less obvious one is blast radius -- an identity that
 * can read every tenant's data exists in exactly one process, is created only
 * under the worker profile, and is granted nothing but SELECT and UPDATE on
 * one table.
 */
public class OutboxStore implements DisposableBean {

    private final HikariDataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    public OutboxStore(OutboxProperties.Datasource config) {
        this.dataSource = new HikariDataSource();
        this.dataSource.setJdbcUrl(config.url());
        this.dataSource.setUsername(config.username());
        this.dataSource.setPassword(config.password());
        // One poller thread, plus headroom for the out-of-band failure write.
        this.dataSource.setMaximumPoolSize(2);
        this.dataSource.setPoolName("outbox-pool");

        this.jdbcTemplate = new JdbcTemplate(this.dataSource);
        this.transactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(this.dataSource));
    }

    /**
     * Claim, publish and mark must share one transaction, so the rows stay
     * locked for the whole round trip. Ending the transaction at the claim
     * would release the locks and let a second replica publish the same
     * events.
     */
    public <T> T inTransaction(Supplier<T> work) {
        return transactionTemplate.execute(status -> work.get());
    }

    /**
     * FOR UPDATE SKIP LOCKED is what makes running more than one worker safe:
     * a second poller walks straight past rows the first has claimed instead
     * of blocking behind them or, worse, publishing them twice. Ordering by
     * id preserves the sequence events were committed in.
     */
    public List<OutboxRecord> claimUnpublished(int limit) {
        return jdbcTemplate.query(
                """
                SELECT id, event_id, org_id, event_type, topic, partition_key, payload::text, traceparent
                FROM outbox_event
                WHERE published_at IS NULL
                ORDER BY id
                LIMIT ?
                FOR UPDATE SKIP LOCKED
                """,
                (rs, rowNum) -> new OutboxRecord(
                        rs.getLong("id"),
                        UUID.fromString(rs.getString("event_id")),
                        rs.getLong("org_id"),
                        rs.getString("event_type"),
                        rs.getString("topic"),
                        rs.getString("partition_key"),
                        rs.getString("payload"),
                        rs.getString("traceparent")),
                limit);
    }

    public void markPublished(List<Long> ids) {
        jdbcTemplate.update(
                "UPDATE outbox_event SET published_at = now() WHERE id IN (" + placeholders(ids) + ")",
                ids.toArray());
    }

    /**
     * Runs in its own transaction, because the one that failed has already
     * rolled back and taken any bookkeeping inside it along. Without this,
     * attempts stays at zero forever and a permanently poisoned row is
     * indistinguishable from a healthy backlog.
     */
    public void recordFailure(List<Long> ids, String error) {
        transactionTemplate.executeWithoutResult(status -> jdbcTemplate.update(
                "UPDATE outbox_event SET attempts = attempts + 1, last_error = ?"
                        + " WHERE id IN (" + placeholders(ids) + ")",
                Stream.concat(Stream.of(error), ids.stream()).toArray()));
    }

    public DataSource dataSource() {
        return dataSource;
    }

    /**
     * Postgres JDBC will not take a Java array as a bind parameter for
     * ANY(?) without wrapping it in a driver-created SQL array, so an
     * expanded IN list is the portable form. The batch size is bounded, and
     * only placeholders are interpolated -- never values.
     */
    private String placeholders(List<Long> ids) {
        return ids.stream().map(id -> "?").collect(Collectors.joining(", "));
    }

    @Override
    public void destroy() {
        dataSource.close();
    }
}
