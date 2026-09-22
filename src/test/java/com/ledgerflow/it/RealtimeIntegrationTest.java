package com.ledgerflow.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.ledgerflow.domain.Account;
import com.ledgerflow.domain.AccountType;
import com.ledgerflow.money.Money;
import com.ledgerflow.realtime.RealtimeBackfillService;
import com.ledgerflow.realtime.RealtimeTicketService;
import com.ledgerflow.repository.AccountRepository;
import com.ledgerflow.service.JournalBuilder;
import com.ledgerflow.service.PostingService;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The two halves of the SSE stream that do not need a real HTTP connection
 * to prove: a ticket stands in for the JWT exactly once, and a reconnecting
 * browser's backfill query reads the same durable log Kafka only transports.
 */
class RealtimeIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private RealtimeTicketService ticketService;

    @Autowired
    private RealtimeBackfillService backfillService;

    @Autowired
    private PostingService postingService;

    @Autowired
    private AccountRepository accountRepository;

    private final JdbcTemplate owner = ownerJdbc();

    @Test
    void aTicketCanBeConsumedExactlyOnce() {
        String ticket = ticketService.issue(DEMO_ORG_ID);

        assertThat(ticketService.consume(ticket)).isEqualTo(DEMO_ORG_ID);
        assertThat(ticketService.consume(ticket)).isNull();
    }

    @Test
    void anUnknownTicketConsumesToNull() {
        assertThat(ticketService.consume(UUID.randomUUID().toString())).isNull();
    }

    @Test
    void backfillReplaysPublishedEventsAfterTheGivenSequence() {
        Long baseline = owner.queryForObject("SELECT COALESCE(MAX(id), 0) FROM outbox_event", Long.class);

        Account debit = newAccount("Realtime Backfill Debit");
        Account credit = newAccount("Realtime Backfill Credit");
        postingService.post(JournalBuilder.forDate(LocalDate.of(2026, 7, 1))
                .withIdempotencyKey("realtime-backfill-" + UUID.randomUUID())
                .describedAs("realtime backfill test")
                .debit(debit.getId(), Money.of("15.00", "USD"))
                .credit(credit.getId(), Money.of("15.00", "USD"))
                .build());

        awaitCondition(() -> owner.queryForObject(
                "SELECT COUNT(*) FROM outbox_event WHERE id > ? AND published_at IS NOT NULL", Long.class, baseline)
                > 0);

        List<RealtimeBackfillService.BackfillEvent> events = backfillService.since(DEMO_ORG_ID, baseline);

        assertThat(events).isNotEmpty();
        assertThat(events).allMatch(e -> e.sequence() > baseline);
        assertThat(events).allMatch(e -> e.eventType().equals("transaction.posted"));
        // Strictly increasing, exactly as a reconnecting browser needs its
        // own Last-Event-ID cursor to advance.
        assertThat(events).isSortedAccordingTo((a, b) -> Long.compare(a.sequence(), b.sequence()));
    }

    private Account newAccount(String name) {
        Account account = new Account();
        account.setCode("T" + UUID.randomUUID().toString().substring(0, 8));
        account.setName(name + " " + UUID.randomUUID());
        account.setCurrency("USD");
        account.setOrgId(DEMO_ORG_ID);
        account.setType(AccountType.ASSET);
        return accountRepository.save(account);
    }
}
