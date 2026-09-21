package com.ledgerflow.web.dto;

import com.ledgerflow.domain.DocumentType;
import jakarta.validation.constraints.NotNull;

public record SettleDocumentRequest(@NotNull DocumentType documentType, @NotNull Long documentId) {}
