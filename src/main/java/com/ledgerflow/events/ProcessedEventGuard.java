package com.ledgerflow.events;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Idempotent consumption for at-least-once Kafka delivery.
 *
 * {@code ON CONFLICT DO NOTHING} rather than a check-then-insert: the two
 * would race under concurrent redelivery (two consumer threads, or a
 * redelivery landing while the first attempt is still mid-transaction), and
 * whichever loses that race would still go on to redo the projection work.
 * The insert itself is the lock -- only one of two concurrent attempts for
 * the same event_id can win it.
 */
@Component
public class ProcessedEventGuard {

    private final JdbcTemplate jdbcTemplate;

    public ProcessedEventGuard(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * @return true if this is the first time {@code consumerGroup} has seen
     *         {@code eventId} -- the caller should do its projection work.
     *         false means it already has, and the caller should skip it.
     * @throws IllegalStateException if called outside the transaction the
     *         projection write belongs in -- see {@link com.ledgerflow.outbox.OutboxRecorder#record}
     *         for why that atomicity matters here too.
     */
    public boolean markProcessed(String consumerGroup, UUID eventId, Long orgId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Idempotent consumption must be recorded inside the projection's own transaction");
        }
        int inserted = jdbcTemplate.update(
                "INSERT INTO processed_event (consumer_group, event_id, org_id) VALUES (?, ?, ?) ON CONFLICT DO NOTHING",
                consumerGroup,
                eventId,
                orgId);
        return inserted == 1;
    }
}
