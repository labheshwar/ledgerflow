package com.ledgerflow.web;

import com.ledgerflow.service.ProjectionService;
import com.ledgerflow.tenancy.TenantContext;
import com.ledgerflow.web.dto.ApAgingRowResponse;
import com.ledgerflow.web.dto.ArAgingRowResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reads straight from the projection the worker keeps current -- see
 * {@link ProjectionService}. Unpaged: an SMB's own open invoices and bills
 * at any one time are a list, not a report, and the full drill-down report
 * (milestone 17) is a different, paged concern.
 */
@RestController
public class AgingController {

    private final ProjectionService projectionService;

    public AgingController(ProjectionService projectionService) {
        this.projectionService = projectionService;
    }

    @GetMapping("/ar-aging")
    public List<ArAgingRowResponse> arAging() {
        return projectionService.listArAging(TenantContext.require()).stream()
                .map(ArAgingRowResponse::from)
                .toList();
    }

    @GetMapping("/ap-aging")
    public List<ApAgingRowResponse> apAging() {
        return projectionService.listApAging(TenantContext.require()).stream()
                .map(ApAgingRowResponse::from)
                .toList();
    }
}
