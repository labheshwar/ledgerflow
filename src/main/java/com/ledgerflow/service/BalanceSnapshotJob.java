package com.ledgerflow.service;

import com.ledgerflow.repository.OrganizationRepository;
import com.ledgerflow.tenancy.TenantContext;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Checkpoints every organization's balances at the end of each month.
 *
 * Worker-only, like every scheduled job here: running this once per web
 * replica would have several processes writing the same snapshots at the
 * same time. The upsert makes that harmless rather than corrupting, but
 * harmless duplicated work is still work.
 *
 * Each organization is processed inside runAs, because a scheduled thread
 * has no request and therefore no tenant -- and without one, row-level
 * security filters every account away and the job would cheerfully
 * checkpoint nothing at all, reporting success.
 */
@Component
@Profile("worker")
public class BalanceSnapshotJob {

    private static final Logger log = LoggerFactory.getLogger(BalanceSnapshotJob.class);

    private final OrganizationRepository organizationRepository;
    private final BalanceSnapshotService snapshotService;

    public BalanceSnapshotJob(
            OrganizationRepository organizationRepository, BalanceSnapshotService snapshotService) {
        this.organizationRepository = organizationRepository;
        this.snapshotService = snapshotService;
    }

    /**
     * 02:00 on the first of the month, checkpointing the month that just
     * ended. Late enough that a late-night posting dated to the closing month
     * is already in; and if something does arrive later still, re-running for
     * that date simply replaces the snapshot.
     */
    @Scheduled(cron = "${ledgerflow.snapshots.cron:0 0 2 1 * *}", zone = "UTC")
    public void snapshotPreviousMonthEnd() {
        LocalDate endOfLastMonth = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1).minusDays(1);
        snapshotAll(endOfLastMonth);
    }

    /** Exposed for operators and tests: checkpoint every organization at a date. */
    public void snapshotAll(LocalDate asOfDate) {
        organizationRepository.findAll().forEach(organization -> {
            try {
                TenantContext.runAs(
                        organization.getId(), () -> snapshotService.snapshotAsOf(asOfDate));
            } catch (RuntimeException e) {
                // One organization's failure must not stop the others. The
                // reads stay correct either way -- a missing snapshot only
                // costs time, never accuracy.
                log.error("Could not snapshot balances for org {} as of {}", organization.getId(), asOfDate, e);
            }
        });
    }
}
