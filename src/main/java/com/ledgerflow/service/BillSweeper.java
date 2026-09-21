package com.ledgerflow.service;

import com.ledgerflow.repository.OrganizationRepository;
import com.ledgerflow.tenancy.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Finishes bills left OPEN with no posting recorded -- the same failure
 * window {@link InvoiceSweeper} closes for invoices, for the same reason:
 * marking a bill OPEN and recording the transaction it posted to are two
 * separately-committed halves, per the project's outer-transaction-trap
 * warning. Retrying is always safe: {@link BillService#post}'s own
 * idempotency key means a retry that already posted just re-reads the same
 * transaction rather than posting twice.
 *
 * Worker-only and per-organization via {@code runAs}, exactly like {@link
 * InvoiceSweeper} -- a scheduled thread has no request and therefore no
 * tenant, and one organization's failure must not stop the rest.
 */
@Component
@Profile("worker")
public class BillSweeper {

    private static final Logger log = LoggerFactory.getLogger(BillSweeper.class);

    private final OrganizationRepository organizationRepository;
    private final BillService billService;

    public BillSweeper(OrganizationRepository organizationRepository, BillService billService) {
        this.organizationRepository = organizationRepository;
        this.billService = billService;
    }

    @Scheduled(fixedDelayString = "${ledgerflow.bills.sweep-interval-ms:60000}")
    public void sweep() {
        organizationRepository.findAll().forEach(organization -> {
            try {
                TenantContext.runAs(organization.getId(), (Runnable) billService::retryStuckPosts);
            } catch (RuntimeException e) {
                log.error("Could not sweep stuck bills for org {}", organization.getId(), e);
            }
        });
    }
}
