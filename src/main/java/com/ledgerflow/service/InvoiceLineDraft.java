package com.ledgerflow.service;

import java.math.BigDecimal;

/** The editable shape of one invoice line, used for both create and update. */
public record InvoiceLineDraft(
        Long itemId, String description, BigDecimal quantity, BigDecimal unitPrice, Long taxRateId) {}
