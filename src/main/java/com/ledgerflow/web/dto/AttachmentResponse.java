package com.ledgerflow.web.dto;

import com.ledgerflow.domain.Attachment;
import java.time.OffsetDateTime;

public record AttachmentResponse(Long id, String filename, String contentType, long sizeBytes, OffsetDateTime createdAt) {

    public static AttachmentResponse from(Attachment attachment) {
        return new AttachmentResponse(
                attachment.getId(),
                attachment.getFilename(),
                attachment.getContentType(),
                attachment.getSizeBytes(),
                attachment.getCreatedAt());
    }
}
