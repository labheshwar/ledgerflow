package com.ledgerflow.realtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Replays what a reconnecting browser missed, straight from the outbox
 * table rather than from Kafka -- the outbox is the durable log Kafka is
 * only a transport for, and it is already scoped by row-level security
 * exactly the way a per-organization stream needs to be.
 *
 * Only published rows: an unpublished one has not been through a
 * projector yet, and handing it to a reconnecting browser ahead of the
 * read models it depends on would show a client-side effect before its
 * own cause.
 *
 * {@code since} is {@code @Transactional} rather than left to whatever
 * transaction a caller happens to be in: {@code OrgAwareJpaTransactionManager}
 * publishes the tenant GUC row-level security reads when a transaction
 * *begins*, so the caller must set {@link com.ledgerflow.tenancy.TenantContext}
 * before entering this method, not from inside it -- entering it is what
 * starts the transaction.
 */
@Service
public class RealtimeBackfillService {

    /** A very stale reconnect misses more than this; that is fine -- the projections it would invalidate on are already current. */
    private static final int MAX_ROWS = 500;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public RealtimeBackfillService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<BackfillEvent> since(long orgId, long afterSequence) {
        return jdbcTemplate.query(
                """
                SELECT id, payload::text AS payload
                FROM outbox_event
                WHERE org_id = ? AND id > ? AND published_at IS NOT NULL
                ORDER BY id
                LIMIT ?
                """,
                (rs, rowNum) -> toEvent(rs.getLong("id"), rs.getString("payload")),
                orgId,
                afterSequence,
                MAX_ROWS);
    }

    private BackfillEvent toEvent(long sequence, String payloadJson) {
        try {
            JsonNode node = objectMapper.readTree(payloadJson);
            return new BackfillEvent(sequence, node.path("eventType").asText("message"), payloadJson);
        } catch (Exception e) {
            throw new IllegalStateException("A stored outbox row was not valid JSON: id=" + sequence, e);
        }
    }

    public record BackfillEvent(long sequence, String eventType, String envelopeJson) {}
}
