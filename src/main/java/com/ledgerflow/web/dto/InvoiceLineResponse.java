package com.ledgerflow.web.dto;

import com.ledgerflow.domain.InvoiceLine;
import com.ledgerflow.service.InvoiceLineTotal;
import java.math.BigDecimal;

public record InvoiceLineResponse(
        Long id,
        Long itemId,
        String description,
        BigDecimal quantity,
        BigDecimal unitPrice,
        Long taxRateId,
        BigDecimal lineSubtotal,
        BigDecimal taxAmount,
        BigDecimal lineTotal) {

    public static InvoiceLineResponse from(InvoiceLine line, InvoiceLineTotal total) {
        return new InvoiceLineResponse(
                line.getId(),
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
