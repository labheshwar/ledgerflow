package com.ledgerflow.service;

import com.ledgerflow.domain.DashboardMetrics;
import com.ledgerflow.repository.DashboardMetricsRepository;
import com.ledgerflow.tenancy.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Serves the dashboard straight from the projection the worker keeps
 * current -- see {@link ProjectionService} -- rather than recomputing the
 * aggregate on every request. The only place that recompute logic lives is
 * still {@link ProjectionService#rebuildAll}, so the numbers a request sees
 * here are exactly the numbers a rebuild would have produced.
 */
@Service
public class DashboardService {

    private final DashboardMetricsRepository dashboardMetricsRepository;
    private final ProjectionService projectionService;

    public DashboardService(DashboardMetricsRepository dashboardMetricsRepository, ProjectionService projectionService) {
        this.dashboardMetricsRepository = dashboardMetricsRepository;
        this.projectionService = projectionService;
    }

    /**
     * A brand-new organization, or one whose very first transaction has not
     * yet made it through the projector, has no row here at all. Computing
     * it on demand -- and writing that computation down as the row a
     * request would otherwise have been waiting for -- means a cold
     * dashboard never has to show "no data yet" while it is technically
     * true and never actually useful.
     */
    @Transactional
    public DashboardSummary getSummary() {
        Long orgId = TenantContext.require();
        DashboardMetrics metrics =
                dashboardMetricsRepository.findById(orgId).orElseGet(() -> projectionService.rebuildAll(orgId));
        return DashboardSummary.from(metrics);
    }
}
