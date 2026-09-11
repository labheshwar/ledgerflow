package com.ledgerflow.web;

import com.ledgerflow.repository.AuditLogRepository;
import com.ledgerflow.web.dto.AuditLogResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/audit-log")
public class AuditLogController {

    private final AuditLogRepository auditLogRepository;

    public AuditLogController(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @GetMapping
    public List<AuditLogResponse> listEntries() {
        return auditLogRepository.findTop200ByOrderByCreatedAtDesc().stream().map(AuditLogResponse::from).toList();
    }
}
