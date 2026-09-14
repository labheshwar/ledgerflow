package com.ledgerflow.outbox;

import java.util.UUID;

/** One claimed, not-yet-published row, as the poller sees it. */
public record OutboxRecord(
        long id,
        UUID eventId,
        long orgId,
        String eventType,
        String topic,
        String partitionKey,
        String payload,
        String traceparent) {}
