package com.ledgerflow.service;

import com.ledgerflow.repository.OrganizationRepository;
import com.ledgerflow.tenancy.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Finishes invoices left SENT with no posting recorded -- the failure window
 * {@link InvoiceService#send} accepts by design, per the project's own
 * outer-transaction-trap warning: marking an invoice SENT and recording the
 * transaction it posted to are two separately-committed halves, and a crash
 * between them (or between posting and that second half) leaves the gap this
 * sweeper closes. Retrying is always safe: {@link InvoiceService#send}'s own
 * idempotency key means a retry that already posted just re-reads the same
 * transaction rather than posting twice.
 *
 * Worker-only and per-organization via {@code runAs}, exactly like
 * {@link BalanceSnapshotJob} -- a scheduled thread has no request and
 * therefore no tenant, and one organization's failure must not stop the rest.
 */
@Component
@Profile("worker")
public class InvoiceSweeper {

    private static final Logger log = LoggerFactory.getLogger(InvoiceSweeper.class);

    private final OrganizationRepository organizationRepository;
    private final InvoiceService invoiceService;

    public InvoiceSweeper(OrganizationRepository organizationRepository, InvoiceService invoiceService) {
        this.organizationRepository = organizationRepository;
        this.invoiceService = invoiceService;
    }

    @Scheduled(fixedDelayString = "${ledgerflow.invoices.sweep-interval-ms:60000}")
    public void sweep() {
        organizationRepository.findAll().forEach(organization -> {
            try {
                TenantContext.runAs(organization.getId(), (Runnable) invoiceService::retryStuckSends);
            } catch (RuntimeException e) {
                log.error("Could not sweep stuck invoices for org {}", organization.getId(), e);
            }
        });
    }
}
