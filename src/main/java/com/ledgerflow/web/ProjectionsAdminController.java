package com.ledgerflow.web;

import com.ledgerflow.service.DashboardSummary;
import com.ledgerflow.service.ProjectionService;
import com.ledgerflow.tenancy.TenantContext;
import com.ledgerflow.web.dto.DashboardSummaryResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A synchronous, on-demand rebuild of every projection for the current
 * organization, straight from source data rather than by replaying Kafka.
 *
 * This is the same recompute {@link com.ledgerflow.events.ProjectionEventListener}
 * runs on every posted transaction -- the endpoint exists to make that
 * recompute triggerable by hand, which is what proves the projector's own
 * central claim: stop it, let the read models go stale, call this, and the
 * numbers land exactly where a running projector would have put them,
 * because both paths are the same absolute-recompute code, not two
 * implementations of the same idea that could quietly disagree.
 */
@RestController
@RequestMapping("/admin/projections")
public class ProjectionsAdminController {

    private final ProjectionService projectionService;

    public ProjectionsAdminController(ProjectionService projectionService) {
        this.projectionService = projectionService;
    }

    @PostMapping("/rebuild")
    public DashboardSummaryResponse rebuild() {
        var metrics = projectionService.rebuildAll(TenantContext.require());
        return DashboardSummaryResponse.from(DashboardSummary.from(metrics));
    }
}
