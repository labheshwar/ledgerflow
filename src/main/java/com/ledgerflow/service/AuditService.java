package com.ledgerflow.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgerflow.domain.AuditLog;
import com.ledgerflow.repository.AuditLogRepository;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

    private static final String SYSTEM_ACTOR = "system";

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuditService(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    public void record(String entityType, Long entityId, String action, Map<String, Object> before, Map<String, Object> after) {
        auditLogRepository.save(new AuditLog(entityType, entityId, action, currentActor(), toJson(before), toJson(after)));
    }

    /**
     * A posting made via the REST API runs with an authenticated
     * SecurityContext, so this is a real username. The reconciliation
     * listener runs on a RabbitMQ consumer thread with no security context
     * at all, so it always falls back to "system".
     */
    private String currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return SYSTEM_ACTOR;
        }
        return authentication.getName();
    }

    private String toJson(Map<String, Object> value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize audit payload", e);
        }
    }
}
