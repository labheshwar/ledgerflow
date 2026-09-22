package com.ledgerflow.web.dto;

import com.ledgerflow.domain.ArAgingEntry;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ArAgingRowResponse(
        Long invoiceId,
        Long contactId,
        String contactName,
        String invoiceNumber,
        LocalDate dueDate,
        String currency,
        BigDecimal balance,
        String bucket) {

    public static ArAgingRowResponse from(ArAgingEntry entry) {
        return new ArAgingRowResponse(
                entry.getInvoiceId(),
                entry.getContactId(),
                entry.getContactName(),
                entry.getInvoiceNumber(),
                entry.getDueDate(),
                entry.getCurrency(),
                entry.getBalance(),
                entry.getBucket().name());
    }
}
