package com.ledgerflow.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgerflow.domain.AuditLog;
import com.ledgerflow.repository.AuditLogRepository;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuditService(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    public void record(String entityType, Long entityId, String action, Map<String, Object> before, Map<String, Object> after) {
        auditLogRepository.save(new AuditLog(entityType, entityId, action, toJson(before), toJson(after)));
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
