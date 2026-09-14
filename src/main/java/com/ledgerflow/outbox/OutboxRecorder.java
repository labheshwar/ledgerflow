package com.ledgerflow.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * The write half of the outbox. Called from inside a business transaction,
 * never on its own.
 *
 * Uses JdbcTemplate rather than an entity deliberately: an outbox row is
 * infrastructure, is written once and never updated by the application, and
 * has no business identity worth managing in the persistence context. It
 * still joins the caller's transaction -- Spring's JdbcTemplate borrows the
 * connection already bound to the thread, which is the same connection JPA
 * is using (see TenancyConfig, which hands the DataSource to the transaction
 * manager so the two share one connection rather than silently opening two).
 *
 * That sharing is the entire point. If this insert committed separately, the
 * outbox would be just another dual write with extra steps.
 */
@Component
public class OutboxRecorder {

    /**
     * Bumped only for a breaking change to an envelope field. Consumers read
     * it to decide whether they understand a message; additive fields do not
     * move it.
     */
    private static final int SCHEMA_VERSION = 1;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<Tracer> tracer;

    public OutboxRecorder(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, ObjectProvider<Tracer> tracer) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.tracer = tracer;
    }

    /**
     * @throws IllegalStateException if no transaction is active -- an outbox
     *         row written outside the transaction it describes provides none
     *         of the atomicity the pattern exists for, and the failure would
     *         otherwise only show up as drift under load.
     */
    public UUID record(
            long orgId,
            String aggregateType,
            String aggregateId,
            String eventType,
            String topic,
            String partitionKey,
            Object payload) {

        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "Outbox events must be recorded inside the transaction that makes the change they describe");
        }

        UUID eventId = UUID.randomUUID();
        String envelope = serialize(new Envelope(
                eventId,
                eventType,
                SCHEMA_VERSION,
                orgId,
                aggregateType,
                aggregateId,
                OffsetDateTime.now(),
                payload));

        jdbcTemplate.update(
                """
                INSERT INTO outbox_event (
                    event_id, org_id, aggregate_type, aggregate_id,
                    event_type, topic, partition_key, payload, traceparent)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)
                """,
                eventId,
                orgId,
                aggregateType,
                aggregateId,
                eventType,
                topic,
                partitionKey,
                envelope,
                currentTraceparent());

        return eventId;
    }

    private String serialize(Envelope envelope) {
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            // Failing the business transaction is the right answer: a
            // transaction that commits without its event is exactly the
            // drift the outbox exists to prevent.
            throw new IllegalStateException("Could not serialize outbox event " + envelope.eventType(), e);
        }
    }

    /**
     * The current span rendered as a W3C traceparent, or null if nothing is
     * being traced. Captured here rather than at publish time because the
     * poller runs on its own thread minutes later and has no connection to
     * the request that caused the event.
     */
    private String currentTraceparent() {
        Tracer activeTracer = tracer.getIfAvailable();
        if (activeTracer == null) {
            return null;
        }
        Span span = activeTracer.currentSpan();
        if (span == null) {
            return null;
        }
        TraceContext context = span.context();
        return "00-%s-%s-%s".formatted(
                context.traceId(), context.spanId(), Boolean.TRUE.equals(context.sampled()) ? "01" : "00");
    }

    /**
     * What actually lands on Kafka. Built once here and stored verbatim, so
     * there is no second serialization path that could disagree with what
     * was recorded.
     */
    private record Envelope(
            UUID eventId,
            String eventType,
            int schemaVersion,
            Long orgId,
            String aggregateType,
            String aggregateId,
            OffsetDateTime occurredAt,
            Object payload) {}
}
