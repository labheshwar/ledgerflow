package com.ledgerflow.service;

import com.ledgerflow.domain.DocumentType;
import java.math.BigDecimal;
import java.time.LocalDate;

/** One invoice or bill still owed against, as offered by a payment's own allocation picker. */
public record OpenDocument(
        DocumentType documentType, Long documentId, String number, LocalDate dueDate, BigDecimal balance, String currency) {}
