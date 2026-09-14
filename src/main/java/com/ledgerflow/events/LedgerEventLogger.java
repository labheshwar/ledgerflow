package com.ledgerflow.events;

import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * The first consumer on the event log. It only logs, and it exists to prove
 * the spine end to end: that an HTTP request's trace id survives the outbox,
 * the broker and the process boundary, and arrives on a worker thread
 * attached to the event it belongs to.
 *
 * The projections in milestone 16 replace this with consumers that build
 * read models. They will deduplicate on eventId -- delivery is at-least-once
 * by design -- and this one does not, because logging a line twice costs
 * nothing.
 */
@Component
@Profile("worker")
public class LedgerEventLogger {

    private static final Logger log = LoggerFactory.getLogger(LedgerEventLogger.class);

    @KafkaListener(topics = LedgerTopics.TRANSACTIONS, groupId = "ledgerflow-event-log")
    public void onLedgerEvent(ConsumerRecord<String, String> record) {
        log.info(
                "event received type={} org={} eventId={} sequence={} partition={} offset={} traceparent={}",
                header(record, "eventType"),
                header(record, "orgId"),
                header(record, "eventId"),
                header(record, "sequence"),
                record.partition(),
                record.offset(),
                header(record, "traceparent"));
    }

    private String header(ConsumerRecord<String, String> record, String key) {
        Header header = record.headers().lastHeader(key);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
