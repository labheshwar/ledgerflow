package com.ledgerflow.outbox;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drains the outbox onto Kafka.
 *
 * A poller rather than change data capture. Debezium would read the write-ahead
 * log and avoid this loop entirely, but it needs wal_level=logical, a
 * replication slot, and a Kafka Connect cluster -- three more moving parts in a
 * stack that has to come up on a laptop. This is roughly a hundred lines,
 * runs on every replica safely, and can be swapped for CDC later without any
 * consumer noticing, because the contract is the topic, not the transport.
 *
 * Delivery is at-least-once: a crash between a successful send and the commit
 * that marks the row published will republish it. That is the deliberate
 * trade -- the alternative, marking rows published before the send, loses
 * events outright. Consumers deduplicate on eventId.
 */
@Component
@Profile("worker")
class OutboxPoller {

    private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);

    private final OutboxStore store;
    private final OutboxPublisher publisher;
    private final OutboxProperties properties;

    OutboxPoller(OutboxStore store, OutboxPublisher publisher, OutboxProperties properties) {
        this.store = store;
        this.publisher = publisher;
        this.properties = properties;
    }

    /**
     * fixedDelay, not fixedRate: the next poll starts after the previous one
     * finished. With fixedRate a slow Kafka would stack overlapping polls on
     * top of each other until the pool starved.
     */
    @Scheduled(fixedDelayString = "${ledgerflow.outbox.poll-interval-ms:500}")
    void drain() {
        List<Long> claimed = List.of();
        try {
            claimed = store.inTransaction(() -> {
                List<OutboxRecord> batch = store.claimUnpublished(properties.batchSize());
                if (batch.isEmpty()) {
                    return List.<Long>of();
                }

                List<Long> ids = batch.stream().map(OutboxRecord::id).toList();
                try {
                    publisher.publish(batch, properties.publishTimeoutSeconds());
                } catch (Exception e) {
                    // Wrapped so the transaction rolls back and the rows stay
                    // locked-then-released as unpublished, rather than being
                    // marked done for events nobody received.
                    throw new PublishFailedException(ids, e);
                }
                store.markPublished(ids);
                log.debug("Published {} outbox events", ids.size());
                return ids;
            });
        } catch (PublishFailedException e) {
            store.recordFailure(e.ids(), describe(e.getCause()));
            log.error("Failed to publish {} outbox events; they stay queued for the next poll", e.ids().size(), e.getCause());
        } catch (RuntimeException e) {
            log.error("Outbox poll failed", e);
        }

        if (claimed.size() >= properties.batchSize()) {
            // A full batch means more is waiting. Sustained full batches are
            // what outbox lag looks like from the inside, and the point at
            // which the poll interval or the batch size needs revisiting.
            log.info("Outbox drained a full batch of {}; backlog is not empty", claimed.size());
        }
    }

    private String describe(Throwable cause) {
        if (cause == null) {
            return "unknown";
        }
        String message = cause.getMessage();
        String text = cause.getClass().getSimpleName() + (message == null ? "" : ": " + message);
        return text.length() > 1000 ? text.substring(0, 1000) : text;
    }

    /** Carries the claimed ids out through the rollback so they can be counted. */
    private static final class PublishFailedException extends RuntimeException {
        private final transient List<Long> ids;

        PublishFailedException(List<Long> ids, Throwable cause) {
            super(cause);
            this.ids = ids;
        }

        List<Long> ids() {
            return ids;
        }
    }
}
