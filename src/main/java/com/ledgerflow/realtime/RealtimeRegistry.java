package com.ledgerflow.realtime;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * The web pods's own local half of the fan-out: which browsers, connected to
 * this specific process, are waiting for which organization's events.
 *
 * Deliberately per-process, in memory, never shared -- Redis pub/sub is
 * what makes the *set of organizations with a live listener somewhere* a
 * cluster-wide fact; which particular pod holds which particular emitter is
 * not a fact anything outside this class needs to know.
 */
@Component
public class RealtimeRegistry {

    private static final Logger log = LoggerFactory.getLogger(RealtimeRegistry.class);

    private final ConcurrentHashMap<Long, Set<SseEmitter>> emittersByOrg = new ConcurrentHashMap<>();

    public void register(long orgId, SseEmitter emitter) {
        Set<SseEmitter> emitters = emittersByOrg.computeIfAbsent(orgId, id -> ConcurrentHashMap.newKeySet());
        emitters.add(emitter);
        emitter.onCompletion(() -> unregister(orgId, emitter));
        emitter.onTimeout(() -> unregister(orgId, emitter));
        emitter.onError(e -> unregister(orgId, emitter));
    }

    private void unregister(long orgId, SseEmitter emitter) {
        Set<SseEmitter> emitters = emittersByOrg.get(orgId);
        if (emitters != null) {
            emitters.remove(emitter);
        }
    }

    /** Called from the Redis subscriber for every message on this organization's channel, however many pods received it. */
    public void dispatch(long orgId, String eventName, String eventId, String data) {
        Set<SseEmitter> emitters = emittersByOrg.get(orgId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().id(eventId).name(eventName).data(data));
            } catch (IOException | IllegalStateException e) {
                // The browser navigated away or the connection dropped; the
                // emitter's own onError/onCompletion callback will unregister
                // it. Logging at debug rather than warn -- a closed tab is
                // the ordinary case, not a fault.
                log.debug("Could not deliver a realtime event to org {}, dropping that emitter", orgId, e);
                unregister(orgId, emitter);
            }
        }
    }
}
