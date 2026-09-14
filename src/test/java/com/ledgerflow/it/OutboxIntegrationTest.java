package com.ledgerflow.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgerflow.domain.Account;
import com.ledgerflow.domain.AccountType;
import com.ledgerflow.domain.EntryType;
import com.ledgerflow.domain.Transaction;
import com.ledgerflow.events.LedgerTopics;
import com.ledgerflow.repository.AccountRepository;
import com.ledgerflow.service.EntryLine;
import com.ledgerflow.service.PostingCommand;
import com.ledgerflow.service.PostingService;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The event spine end to end: a posting writes an outbox row in its own
 * transaction, the poller moves it onto Kafka, and the trace that started in
 * the caller survives all the way to the consumer.
 *
 * Reads the outbox as the owner. The table is tenant-scoped like every other,
 * and these assertions need to see rows regardless of which organization is
 * in context.
 */
class OutboxIntegrationTest extends AbstractIntegrationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private PostingService postingService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private Tracer tracer;

    private final JdbcTemplate owner = ownerJdbc();

    @Test
    void aPostingLandsOnKafkaCarryingTheTraceThatCausedIt() throws Exception {
        Account debit = newAccount("Outbox Debit");
        Account credit = newAccount("Outbox Credit");
        String idempotencyKey = "outbox-" + UUID.randomUUID();

        // A span stands in for the HTTP request that would normally be in
        // flight. Without one there is nothing to propagate and the
        // interesting half of this test would pass vacuously.
        Span span = tracer.nextSpan().name("post-transaction-test").start();
        Transaction transaction;
        try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
            transaction = postingService.post(new PostingCommand(
                    idempotencyKey,
                    "outbox integration test",
                    List.of(
                            new EntryLine(debit.getId(), EntryType.DEBIT, new BigDecimal("25.00")),
                            new EntryLine(credit.getId(), EntryType.CREDIT, new BigDecimal("25.00")))));
        } finally {
            span.end();
        }
        String traceId = span.context().traceId();

        Map<String, Object> row = owner.queryForMap(
                "SELECT * FROM outbox_event WHERE aggregate_type = 'TRANSACTION' AND aggregate_id = ?",
                String.valueOf(transaction.getId()));

        assertThat(row.get("event_type")).isEqualTo("transaction.posted");
        assertThat(row.get("topic")).isEqualTo(LedgerTopics.TRANSACTIONS);
        assertThat(row.get("org_id")).isEqualTo(DEMO_ORG_ID);
        assertThat(row.get("partition_key")).isEqualTo("org-" + DEMO_ORG_ID);
        assertThat((String) row.get("traceparent")).startsWith("00-" + traceId + "-");

        JsonNode envelope = MAPPER.readTree(String.valueOf(row.get("payload")));
        assertThat(envelope.get("schemaVersion").asInt()).isEqualTo(1);
        assertThat(envelope.get("eventType").asText()).isEqualTo("transaction.posted");
        assertThat(envelope.get("orgId").asLong()).isEqualTo(DEMO_ORG_ID);
        assertThat(envelope.get("payload").get("idempotencyKey").asText()).isEqualTo(idempotencyKey);
        assertThat(envelope.get("payload").get("entries")).hasSize(2);

        UUID eventId = UUID.fromString(String.valueOf(row.get("event_id")));
        ConsumerRecord<String, String> delivered = awaitEvent(eventId);

        assertThat(delivered.key()).isEqualTo("org-" + DEMO_ORG_ID);
        assertThat(header(delivered, "eventType")).isEqualTo("transaction.posted");
        assertThat(header(delivered, "orgId")).isEqualTo(String.valueOf(DEMO_ORG_ID));
        assertThat(header(delivered, "sequence")).isEqualTo(String.valueOf(row.get("id")));
        // The point of the whole exercise: one trace id spanning an inbound
        // call, a database transaction, a poller on another thread, and a
        // broker hop.
        assertThat(header(delivered, "traceparent")).startsWith("00-" + traceId + "-");
        assertThat(delivered.value()).isEqualTo(String.valueOf(row.get("payload")));

        // Published rows are marked, so the next poll walks past them
        // instead of sending the same event forever.
        awaitCondition(() -> owner.queryForObject(
                "SELECT published_at IS NOT NULL FROM outbox_event WHERE event_id = ?", Boolean.class, eventId));
        assertThat(owner.queryForObject(
                        "SELECT attempts FROM outbox_event WHERE event_id = ?", Integer.class, eventId))
                .isZero();
    }

    @Test
    void replayingAPostingProducesExactlyOneEvent() {
        Account debit = newAccount("Outbox Idempotent Debit");
        Account credit = newAccount("Outbox Idempotent Credit");
        PostingCommand command = new PostingCommand(
                "outbox-idem-" + UUID.randomUUID(),
                "retried by an impatient client",
                List.of(
                        new EntryLine(debit.getId(), EntryType.DEBIT, new BigDecimal("10.00")),
                        new EntryLine(credit.getId(), EntryType.CREDIT, new BigDecimal("10.00"))));

        Transaction first = postingService.post(command);
        Transaction second = postingService.post(command);

        assertThat(second.getId()).isEqualTo(first.getId());

        // Idempotency has to hold for the event log too. A second event here
        // would be counted twice by every downstream projection -- revenue
        // reported double, with no error anywhere to explain it.
        assertThat(owner.queryForObject(
                        "SELECT count(*) FROM outbox_event WHERE aggregate_type = 'TRANSACTION' AND aggregate_id = ?",
                        Long.class,
                        String.valueOf(first.getId())))
                .isEqualTo(1);
    }

    @Test
    void anOutboxRowAndItsTransactionCommitTogether() {
        Account debit = newAccount("Outbox Atomic Debit");
        Account credit = newAccount("Outbox Atomic Credit");
        Transaction transaction = postingService.post(new PostingCommand(
                "outbox-atomic-" + UUID.randomUUID(),
                "atomicity check",
                List.of(
                        new EntryLine(debit.getId(), EntryType.DEBIT, new BigDecimal("5.00")),
                        new EntryLine(credit.getId(), EntryType.CREDIT, new BigDecimal("5.00")))));

        // Both rows are visible to a connection that was never part of the
        // writing transaction, which is only possible if they committed
        // together. The failure mode this guards against is a JdbcTemplate
        // that quietly opened its own connection -- what happens when the
        // transaction manager is never given a DataSource -- which would put
        // the outbox write outside the transaction it is supposed to be
        // atomic with, and outside the tenant context as well.
        assertThat(owner.queryForObject(
                        "SELECT count(*) FROM transactions WHERE id = ?", Long.class, transaction.getId()))
                .isEqualTo(1);
        assertThat(owner.queryForObject(
                        "SELECT count(*) FROM outbox_event WHERE aggregate_id = ?",
                        Long.class,
                        String.valueOf(transaction.getId())))
                .isEqualTo(1);
    }

    private Account newAccount(String name) {
        Account account = new Account();
        account.setName(name + " " + UUID.randomUUID());
        account.setCurrency("USD");
        account.setOrgId(DEMO_ORG_ID);
        account.setType(AccountType.ASSET);
        return accountRepository.save(account);
    }

    /**
     * A throwaway consumer group reading from the beginning of the topic, so
     * the test does not race the poller: whether the event was published
     * before or after this subscribes, it is found either way.
     */
    private ConsumerRecord<String, String> awaitEvent(UUID eventId) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "outbox-it-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(LedgerTopics.TRANSACTIONS));
            long deadline = System.currentTimeMillis() + 60_000;
            while (System.currentTimeMillis() < deadline) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    if (eventId.toString().equals(header(record, "eventId"))) {
                        return record;
                    }
                }
            }
        }
        throw new AssertionError("Event " + eventId + " never arrived on " + LedgerTopics.TRANSACTIONS);
    }

    private String header(ConsumerRecord<String, String> record, String key) {
        Header header = record.headers().lastHeader(key);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
