package com.ledgerflow.web.dto;

import com.ledgerflow.domain.DocumentType;
import com.ledgerflow.service.OpenDocument;
import java.math.BigDecimal;
import java.time.LocalDate;

public record OpenDocumentResponse(
        DocumentType documentType, Long documentId, String number, LocalDate dueDate, BigDecimal balance, String currency) {

    public static OpenDocumentResponse from(OpenDocument document) {
        return new OpenDocumentResponse(
                document.documentType(),
                document.documentId(),
                document.number(),
                document.dueDate(),
                document.balance(),
                document.currency());
    }
}
