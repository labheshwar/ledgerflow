package com.ledgerflow.service;

import com.ledgerflow.domain.Attachment;
import com.ledgerflow.repository.AttachmentRepository;
import com.ledgerflow.storage.ObjectStorageService;
import com.ledgerflow.tenancy.TenantContext;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Files attached to a document -- an invoice today, whatever else needs one
 * later. The row is metadata only; {@link ObjectStorageService} holds the
 * bytes.
 */
@Service
public class AttachmentService {

    private final AttachmentRepository attachmentRepository;
    private final ObjectStorageService objectStorageService;

    public AttachmentService(AttachmentRepository attachmentRepository, ObjectStorageService objectStorageService) {
        this.attachmentRepository = attachmentRepository;
        this.objectStorageService = objectStorageService;
    }

    public List<Attachment> list(String entityType, Long entityId) {
        return attachmentRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(entityType, entityId);
    }

    @Transactional
    public Attachment upload(String entityType, Long entityId, String filename, String contentType, byte[] content) {
        Long orgId = TenantContext.require();
        String storageKey = "%s/%d/%s-%s".formatted(entityType.toLowerCase(), entityId, UUID.randomUUID(), filename);

        // Uploaded before the row is saved: a row referencing an object that
        // never made it to the store is a broken download link, but an
        // orphaned object with no row is invisible and harmless.
        objectStorageService.put(storageKey, content, contentType);

        Attachment attachment = new Attachment();
        attachment.setOrgId(orgId);
        attachment.setEntityType(entityType);
        attachment.setEntityId(entityId);
        attachment.setFilename(filename);
        attachment.setContentType(contentType);
        attachment.setSizeBytes(content.length);
        attachment.setStorageKey(storageKey);
        attachment.setCreatedAt(OffsetDateTime.now());
        return attachmentRepository.save(attachment);
    }

    public byte[] download(Long attachmentId) {
        return objectStorageService.get(require(attachmentId).getStorageKey());
    }

    public Attachment get(Long attachmentId) {
        return require(attachmentId);
    }

    @Transactional
    public void delete(Long attachmentId) {
        Attachment attachment = require(attachmentId);
        objectStorageService.delete(attachment.getStorageKey());
        attachmentRepository.delete(attachment);
    }

    private Attachment require(Long id) {
        return attachmentRepository.findById(id).orElseThrow(() -> new NoSuchElementException("No attachment with id " + id));
    }
}
