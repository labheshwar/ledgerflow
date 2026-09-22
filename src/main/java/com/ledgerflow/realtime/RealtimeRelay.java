package com.ledgerflow.realtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * The publish half of the SSE fan-out. A durable event (one already written
 * to the outbox and delivered off Kafka) is republished here, over Redis
 * pub/sub, to whichever web pod is holding the browser connection for that
 * organization -- there is no guarantee it is this process.
 *
 * Deliberately not the same path a projector's own database write commits
 * in: Redis pub/sub has no memory, a message published to no subscriber is
 * simply gone, and that is fine here specifically because the outbox row
 * this republishes is never gone -- a reconnecting browser backfills the
 * ones it missed straight from Postgres. See RealtimeBackfillService.
 */
@Component
public class RealtimeRelay {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RealtimeRelay(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * @param sequence the outbox row's own id -- what a reconnecting browser
     *        sends back as Last-Event-ID, so it has to be the SSE frame's
     *        {@code id}, not anything derived from the envelope's contents.
     * @param envelopeJson the exact bytes the outbox recorded and Kafka
     *        carried, republished verbatim rather than re-serialized, for
     *        the same reason OutboxPublisher sends the stored payload as-is.
     */
    public void publish(long orgId, long sequence, String envelopeJson) {
        ObjectNode wrapper = objectMapper.createObjectNode();
        wrapper.put("sequence", sequence);
        wrapper.setAll((ObjectNode) parseOrWrap(envelopeJson));
        redisTemplate.convertAndSend(RealtimeChannels.forOrg(orgId), wrapper.toString());
    }

    private JsonNode parseOrWrap(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Not valid JSON to relay: " + e.getMessage(), e);
        }
    }
}
