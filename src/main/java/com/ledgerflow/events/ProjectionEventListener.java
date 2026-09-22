package com.ledgerflow.events;

import com.ledgerflow.realtime.RealtimeRelay;
import com.ledgerflow.service.ProjectionService;
import com.ledgerflow.tenancy.TenantContext;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * The projector: turns every posted transaction into a fresh dashboard and
 * aging read model, and republishes the same event over Redis for whichever
 * web pod is holding the browser connection for that organization.
 *
 * A separate consumer group from {@link LedgerEventLogger} -- Kafka tracks
 * offsets per group, so this one can be reset and replayed from the
 * beginning of the topic without disturbing the logger, or any future
 * consumer, that shares the topic but not the group.
 */
@Component
@Profile("worker")
public class ProjectionEventListener {

    private static final Logger log = LoggerFactory.getLogger(ProjectionEventListener.class);
    private static final String CONSUMER_GROUP = "ledgerflow-projections";

    private final ProjectionService projectionService;
    private final RealtimeRelay realtimeRelay;

    public ProjectionEventListener(ProjectionService projectionService, RealtimeRelay realtimeRelay) {
        this.projectionService = projectionService;
        this.realtimeRelay = realtimeRelay;
    }

    @KafkaListener(topics = LedgerTopics.TRANSACTIONS, groupId = CONSUMER_GROUP)
    public void onTransactionPosted(ConsumerRecord<String, String> record) {
        UUID eventId = UUID.fromString(header(record, "eventId"));
        long orgId = Long.parseLong(header(record, "orgId"));
        long sequence = Long.parseLong(header(record, "sequence"));

        boolean applied =
                TenantContext.runAs(orgId, () -> projectionService.applyTransactionPosted(CONSUMER_GROUP, eventId, orgId));
        if (!applied) {
            log.debug("Skipping already-processed event {} for org {}", eventId, orgId);
            return;
        }

        // Published after the projection transaction has committed, not
        // from inside it -- a browser reacting to this by refetching should
        // never be able to observe the read model before the write that
        // produced it did.
        realtimeRelay.publish(orgId, sequence, record.value());
    }

    private String header(ConsumerRecord<String, String> record, String key) {
        Header header = record.headers().lastHeader(key);
        if (header == null) {
            throw new IllegalStateException("Outbox event on " + LedgerTopics.TRANSACTIONS + " is missing header " + key);
        }
        return new String(header.value(), StandardCharsets.UTF_8);
    }
}
