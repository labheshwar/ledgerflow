package com.ledgerflow.realtime;

import java.time.Duration;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Single-use tickets that stand in for the JWT on the one connection that
 * cannot carry one: {@code EventSource} has no way to set an Authorization
 * header, and a token in the URL would land in nginx access logs and every
 * {@code Referer} header the browser ever sends from that page.
 *
 * A ticket is deliberately short-lived and burned on first use -- it only
 * has to survive the round trip from "fetch a ticket" to "open the stream",
 * a few hundred milliseconds in practice, never a whole session.
 */
@Service
public class RealtimeTicketService {

    private static final String KEY_PREFIX = "rt:ticket:";
    private static final Duration TTL = Duration.ofSeconds(30);

    private final StringRedisTemplate redisTemplate;

    public RealtimeTicketService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public String issue(long orgId) {
        String ticket = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(KEY_PREFIX + ticket, String.valueOf(orgId), TTL);
        return ticket;
    }

    /** @return the org the ticket was issued for, or null if it is unknown, expired, or already used. */
    public Long consume(String ticket) {
        String key = KEY_PREFIX + ticket;
        String orgId = redisTemplate.opsForValue().get(key);
        if (orgId == null) {
            return null;
        }
        redisTemplate.delete(key);
        return Long.parseLong(orgId);
    }
}
