package com.ledgerflow.web;

import com.ledgerflow.repository.AuditLogRepository;
import com.ledgerflow.web.dto.AuditLogResponse;
import com.ledgerflow.web.dto.PagedResponse;
import java.util.Set;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/audit-log")
public class AuditLogController {

    private static final Set<String> SORTABLE = Set.of("createdAt", "actor", "action", "entityType");

    private final AuditLogRepository auditLogRepository;

    public AuditLogController(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @GetMapping
    public PagedResponse<AuditLogResponse> listEntries(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String entityType,
            @ParameterObject @PageableDefault(size = 25) Pageable pageable) {

        Pageable sorted = SortWhitelist.apply(pageable, SORTABLE, Sort.by("createdAt").descending());
        return PagedResponse.from(auditLogRepository.search(q, entityType, sorted), AuditLogResponse::from);
    }
}
