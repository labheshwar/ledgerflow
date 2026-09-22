package com.ledgerflow.web.dto;

import com.ledgerflow.domain.ApAgingEntry;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ApAgingRowResponse(
        Long billId,
        Long contactId,
        String contactName,
        String billNumber,
        LocalDate dueDate,
        String currency,
        BigDecimal balance,
        String bucket) {

    public static ApAgingRowResponse from(ApAgingEntry entry) {
        return new ApAgingRowResponse(
                entry.getBillId(),
                entry.getContactId(),
                entry.getContactName(),
                entry.getBillNumber(),
                entry.getDueDate(),
                entry.getCurrency(),
                entry.getBalance(),
                entry.getBucket().name());
    }
}
