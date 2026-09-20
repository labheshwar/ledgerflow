package com.ledgerflow.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ledgerflow.domain.DocumentType;
import com.ledgerflow.service.DocumentNumberingService;
import com.ledgerflow.tenancy.TenantContext;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Gapless document numbering, proven against real Postgres: the increment
 * has to be a single atomic statement under real concurrency, and it has to
 * actually roll back with the transaction that failed, neither of which a
 * mocked repository could demonstrate.
 *
 * Every draw here runs inside an explicit transaction, standing in for the
 * document-creating service method that will wrap it for real from
 * milestone 9 onward -- DocumentNumberingService deliberately opens none of
 * its own, so the increment lives or dies with whatever transaction the
 * caller is already in (see its own Javadoc). A draw with no ambient
 * transaction at all has nothing to set the tenant for row-level security,
 * and Postgres refuses the insert -- which is this test file's job to lean
 * on, not paper over.
 */
class DocumentNumberingIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private DocumentNumberingService documentNumberingService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final JdbcTemplate owner = ownerJdbc();

    /**
     * A fresh template per call rather than a field built at construction:
     * {@code transactionManager} is only populated by {@code @Autowired}
     * after every field initializer has already run, so building this once
     * as a field would capture it while still null.
     */
    private TransactionTemplate tx() {
        return new TransactionTemplate(transactionManager);
    }

    private String draw(DocumentType type) {
        return tx().execute(status -> documentNumberingService.next(type));
    }

    @Test
    void numbersIncreaseByOneAndArePaddedWithAPrefix() {
        String first = draw(DocumentType.INVOICE);
        String second = draw(DocumentType.INVOICE);

        assertThat(first).matches("INV-\\d{5}");
        assertThat(second).matches("INV-\\d{5}");
        assertThat(Integer.parseInt(second.substring(4))).isEqualTo(Integer.parseInt(first.substring(4)) + 1);
    }

    @Test
    void invoicesAndBillsAreSeparateSeries() {
        String invoice = draw(DocumentType.INVOICE);
        String bill = draw(DocumentType.BILL);

        assertThat(invoice).startsWith("INV-");
        assertThat(bill).startsWith("BILL-");
    }

    @Test
    void twoOrganizationsEachGetTheirOwnSeriesStartingAtOne() {
        Long bareOrgId = owner.queryForObject(
                "INSERT INTO organizations (name, base_currency) VALUES (?, 'USD') RETURNING id",
                Long.class,
                "Numbering Test Org " + UUID.randomUUID());

        // The demo org's own series is already past 1 from other tests in
        // this class; the point is that a brand-new org is unaffected by it
        // and starts its own series at 1, not wherever the shared table
        // happens to be for a different tenant.
        String demoOrgNumber = draw(DocumentType.BILL);
        String otherOrgFirstNumber = TenantContext.runAs(bareOrgId, () -> draw(DocumentType.BILL));

        assertThat(demoOrgNumber).startsWith("BILL-");
        assertThat(otherOrgFirstNumber).isEqualTo("BILL-00001");
    }

    @Test
    void aRolledBackTransactionDoesNotConsumeANumber() {
        String before = draw(DocumentType.INVOICE);
        int beforeNumber = Integer.parseInt(before.substring(4));

        assertThatThrownBy(() -> tx().execute(status -> {
                    // Standing in for "the document failed to save after its
                    // number was drawn" -- the increment and the failure
                    // share this one transaction, exactly as they would with
                    // a real document insert.
                    documentNumberingService.next(DocumentType.INVOICE);
                    throw new RuntimeException("simulated failure after drawing a number");
                }))
                .isInstanceOf(RuntimeException.class);

        String after = draw(DocumentType.INVOICE);
        int afterNumber = Integer.parseInt(after.substring(4));

        // Exactly one successful increment happened between the two reads
        // above -- the one inside the rolled-back transaction left no
        // trace, which a Postgres SEQUENCE never would.
        assertThat(afterNumber).isEqualTo(beforeNumber + 1);
    }

    @Test
    void concurrentRequestsForTheSameOrganizationNeverCollideOrSkip() throws Exception {
        int attempts = 12;
        ExecutorService pool = Executors.newFixedThreadPool(6);
        try {
            // Every task establishes its own tenant context and its own
            // transaction on whichever pool thread runs it -- TenantContext
            // is thread-local and the transaction manager reads it fresh in
            // doBegin, so nothing here relies on state left over from the
            // test's own thread.
            Callable<String> attempt = () -> TenantContext.runAs(DEMO_ORG_ID, () -> draw(DocumentType.BILL));

            var futures = IntStream.range(0, attempts).<Future<String>>mapToObj(i -> pool.submit(attempt)).toList();

            var numbers = futures.stream()
                    .map(f -> {
                        try {
                            return Integer.parseInt(f.get().substring(5));
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .sorted()
                    .toList();

            // No two concurrent callers drew the same number, and none was
            // skipped in between -- the run is a contiguous block once
            // sorted, which is only possible if every increment really
            // locked the row rather than reading-then-writing racily.
            for (int i = 1; i < numbers.size(); i++) {
                assertThat(numbers.get(i)).isEqualTo(numbers.get(i - 1) + 1);
            }
        } finally {
            pool.shutdown();
        }
    }
}
