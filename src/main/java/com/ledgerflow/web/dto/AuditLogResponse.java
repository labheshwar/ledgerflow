package com.ledgerflow.web.dto;

import com.ledgerflow.domain.AuditLog;
import java.time.OffsetDateTime;

public record AuditLogResponse(
        Long id,
        String entityType,
        Long entityId,
        String action,
        String actor,
        String beforeState,
        String afterState,
        OffsetDateTime createdAt) {

    public static AuditLogResponse from(AuditLog auditLog) {
        return new AuditLogResponse(
                auditLog.getId(),
                auditLog.getEntityType(),
                auditLog.getEntityId(),
                auditLog.getAction(),
                auditLog.getActor(),
                auditLog.getBeforeState(),
                auditLog.getAfterState(),
                auditLog.getCreatedAt());
    }
}
