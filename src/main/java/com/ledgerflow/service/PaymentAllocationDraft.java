package com.ledgerflow.service;

import com.ledgerflow.domain.DocumentType;
import java.math.BigDecimal;

/** How much of a payment being recorded settles one particular invoice or bill. */
public record PaymentAllocationDraft(DocumentType documentType, Long documentId, BigDecimal amount) {}
