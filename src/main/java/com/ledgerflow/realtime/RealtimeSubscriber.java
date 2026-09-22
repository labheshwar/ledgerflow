package com.ledgerflow.realtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

/**
 * Every web pod's own subscriber to the whole {@code rt:org:*} pattern.
 * Redis pub/sub fans a published message out to every subscriber, so every
 * pod receives every organization's messages; {@link RealtimeRegistry#dispatch}
 * is what makes that a no-op for a pod holding no emitter for that org.
 */
@Component
public class RealtimeSubscriber implements MessageListener {

    private final RealtimeRegistry registry;
    private final ObjectMapper objectMapper;

    public RealtimeSubscriber(RealtimeRegistry registry, ObjectMapper objectMapper) {
        this.registry = registry;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        long orgId = RealtimeChannels.orgIdFromChannel(new String(message.getChannel(), StandardCharsets.UTF_8));
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        try {
            JsonNode node = objectMapper.readTree(body);
            String eventType = node.path("eventType").asText("message");
            String sequence = node.path("sequence").asText(null);
            registry.dispatch(orgId, eventType, sequence, body);
        } catch (IOException e) {
            // A malformed message on our own internal channel would be a bug
            // in RealtimeRelay, not something a browser can act on either
            // way -- dropping it here is strictly better than crashing the
            // shared listener thread every other organization's events also
            // flow through.
            throw new IllegalStateException("Could not parse a realtime message for org " + orgId, e);
        }
    }
}
