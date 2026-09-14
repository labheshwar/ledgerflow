package com.ledgerflow.outbox;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Moves claimed rows onto Kafka. Deliberately knows nothing about event
 * shapes -- the envelope was serialized when the row was written, and this
 * sends the stored bytes verbatim.
 */
@Component
@Profile("worker")
class OutboxPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;

    OutboxPublisher(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Sends the whole batch, then waits once. Awaiting each send individually
     * would serialize the round trips and throw away the producer's batching
     * -- 500 events would take 500 network waits instead of a handful.
     *
     * @throws Exception if any send fails, which rolls the caller's
     *         transaction back so the rows stay unpublished and are retried.
     */
    void publish(List<OutboxRecord> batch, int timeoutSeconds) throws Exception {
        List<CompletableFuture<?>> sends = new ArrayList<>(batch.size());
        for (OutboxRecord event : batch) {
            sends.add(kafkaTemplate.send(toProducerRecord(event)));
        }
        CompletableFuture.allOf(sends.toArray(CompletableFuture[]::new)).get(timeoutSeconds, TimeUnit.SECONDS);
    }

    private ProducerRecord<String, String> toProducerRecord(OutboxRecord event) {
        ProducerRecord<String, String> record =
                new ProducerRecord<>(event.topic(), null, event.partitionKey(), event.payload());

        // Headers, not payload fields, so a consumer can route, deduplicate
        // or filter without paying to deserialize the body.
        header(record, "eventId", event.eventId().toString());
        header(record, "eventType", event.eventType());
        header(record, "orgId", String.valueOf(event.orgId()));
        // The outbox row id. Monotonic per publisher, and the cursor the SSE
        // stream will replay from after a reconnect.
        header(record, "sequence", String.valueOf(event.id()));
        if (event.traceparent() != null) {
            // The W3C header name exactly, so any OpenTelemetry-aware
            // consumer continues the trace without bespoke glue.
            header(record, "traceparent", event.traceparent());
        }
        return record;
    }

    private void header(ProducerRecord<String, String> record, String key, String value) {
        record.headers().add(key, value.getBytes(StandardCharsets.UTF_8));
    }
}
