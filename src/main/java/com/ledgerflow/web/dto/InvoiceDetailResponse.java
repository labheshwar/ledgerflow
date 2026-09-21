package com.ledgerflow.web.dto;

import com.ledgerflow.domain.Invoice;
import com.ledgerflow.domain.InvoiceLine;
import com.ledgerflow.domain.InvoiceStatus;
import com.ledgerflow.service.InvoiceTotals;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.IntStream;

public record InvoiceDetailResponse(
        Long id,
        Long contactId,
        String contactName,
        String invoiceNumber,
        InvoiceStatus status,
        LocalDate issueDate,
        LocalDate dueDate,
        String currency,
        String notes,
        BigDecimal subtotal,
        BigDecimal taxTotal,
        BigDecimal grandTotal,
        BigDecimal amountPaid,
        BigDecimal balanceDue,
        boolean paid,
        Long postedTransactionId,
        boolean overdue,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<InvoiceLineResponse> lines) {

    public static InvoiceDetailResponse from(
            Invoice invoice, String contactName, List<InvoiceLine> lines, InvoiceTotals totals, BigDecimal amountPaid) {
        BigDecimal balanceDue = totals.grandTotal().subtract(amountPaid);
        boolean paid = invoice.getStatus() == InvoiceStatus.SENT && balanceDue.signum() <= 0;
        boolean overdue = invoice.getStatus() == InvoiceStatus.SENT && !paid && invoice.getDueDate().isBefore(LocalDate.now());
        List<InvoiceLineResponse> lineResponses = IntStream.range(0, lines.size())
                .mapToObj(i -> InvoiceLineResponse.from(lines.get(i), totals.lines().get(i)))
                .toList();
        return new InvoiceDetailResponse(
                invoice.getId(),
                invoice.getContactId(),
                contactName,
                invoice.getInvoiceNumber(),
                invoice.getStatus(),
                invoice.getIssueDate(),
                invoice.getDueDate(),
                invoice.getCurrency(),
                invoice.getNotes(),
                totals.subtotal(),
                totals.taxTotal(),
                totals.grandTotal(),
                amountPaid,
                balanceDue,
                paid,
                invoice.getPostedTransactionId(),
                overdue,
                invoice.getCreatedAt(),
                invoice.getUpdatedAt(),
                lineResponses);
    }
}
