package com.ledgerflow.web;

import com.ledgerflow.exception.RealtimeException;
import com.ledgerflow.realtime.RealtimeBackfillService;
import com.ledgerflow.realtime.RealtimeRegistry;
import com.ledgerflow.realtime.RealtimeTicketService;
import com.ledgerflow.tenancy.TenantContext;
import com.ledgerflow.web.dto.RealtimeTicketResponse;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * The two halves of live push: a normal, JWT-authenticated call to get a
 * one-time ticket, and the actual stream, which {@code EventSource} opens
 * with no Authorization header of its own -- see {@link RealtimeTicketService}
 * for why a ticket stands in for the JWT here rather than the token itself.
 */
@RestController
@RequestMapping("/events")
public class RealtimeController {

    private final RealtimeTicketService ticketService;
    private final RealtimeRegistry registry;
    private final RealtimeBackfillService backfillService;

    public RealtimeController(
            RealtimeTicketService ticketService, RealtimeRegistry registry, RealtimeBackfillService backfillService) {
        this.ticketService = ticketService;
        this.registry = registry;
        this.backfillService = backfillService;
    }

    @PostMapping("/ticket")
    public RealtimeTicketResponse issueTicket() {
        return new RealtimeTicketResponse(ticketService.issue(TenantContext.require()));
    }

    /**
     * No {@code @RequestParam Long lastEventId} default -- a first-ever
     * connection has nothing to backfill, and treating "no cursor" as "from
     * the beginning of this organization's history" would replay its whole
     * lifetime at every fresh page load.
     *
     * Registers with {@link RealtimeRegistry} before running the backfill
     * query, not after: an event published in between would then be
     * delivered twice (once live, once replayed) rather than dropped
     * entirely. A duplicate is harmless -- every consumer of this stream
     * reacts to it by invalidating a cache, which is idempotent by nature.
     * A gap is not recoverable at all.
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @RequestParam String ticket,
            @RequestParam(required = false) Long lastEventId,
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventIdHeader) {
        Long orgId = ticketService.consume(ticket);
        if (orgId == null) {
            throw new RealtimeException("INVALID_TICKET", "This ticket is unknown, expired, or already used");
        }

        SseEmitter emitter = new SseEmitter(0L);
        registry.register(orgId, emitter);

        Long cursor = lastEventId != null ? lastEventId : parseLong(lastEventIdHeader);
        if (cursor != null) {
            // This connection carries no JWT, so nothing else has set the
            // tenant for this thread -- and it has to be set before
            // backfillService.since is entered, not from inside it, since
            // that is where the transaction (and the RLS GUC) begins.
            List<RealtimeBackfillService.BackfillEvent> events =
                    TenantContext.runAs(orgId, () -> backfillService.since(orgId, cursor));
            for (RealtimeBackfillService.BackfillEvent event : events) {
                try {
                    emitter.send(SseEmitter.event()
                            .id(String.valueOf(event.sequence()))
                            .name(event.eventType())
                            .data(event.envelopeJson()));
                } catch (Exception e) {
                    // The connection died mid-backfill; nothing left to send to.
                    return emitter;
                }
            }
        }

        return emitter;
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
