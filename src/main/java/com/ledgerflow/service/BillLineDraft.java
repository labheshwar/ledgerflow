package com.ledgerflow.service;

import java.math.BigDecimal;

/** The editable shape of one bill line, used for both create and update. */
public record BillLineDraft(
        Long accountId, Long itemId, String description, BigDecimal quantity, BigDecimal unitPrice, Long taxRateId) {}
