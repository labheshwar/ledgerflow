package com.ledgerflow.web.dto;

import com.ledgerflow.domain.BillLine;
import com.ledgerflow.service.InvoiceLineTotal;
import java.math.BigDecimal;

public record BillLineResponse(
        Long id,
        Long accountId,
        Long itemId,
        String description,
        BigDecimal quantity,
        BigDecimal unitPrice,
        Long taxRateId,
        BigDecimal lineSubtotal,
        BigDecimal taxAmount,
        BigDecimal lineTotal) {

    public static BillLineResponse from(BillLine line, InvoiceLineTotal total) {
        return new BillLineResponse(
                line.getId(),
                line.getAccountId(),
                line.getItemId(),
                line.getDescription(),
                line.getQuantity(),
                line.getUnitPrice(),
                line.getTaxRateId(),
                total.lineSubtotal(),
                total.taxAmount(),
                total.lineTotal());
    }
}
