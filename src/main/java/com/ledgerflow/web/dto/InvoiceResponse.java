package com.ledgerflow.web.dto;

import com.ledgerflow.domain.Invoice;
import com.ledgerflow.domain.InvoiceStatus;
import com.ledgerflow.service.InvoiceTotals;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * @param overdue derived from {@code status} and {@code dueDate} on every
 *        read, never stored -- a due date does not change, so there is
 *        nothing to keep in sync. "Paid" is not derivable yet: nothing
 *        records a payment against an invoice until milestone 12.
 */
public record InvoiceResponse(
        Long id,
        Long contactId,
        String contactName,
        String invoiceNumber,
        InvoiceStatus status,
        LocalDate issueDate,
        LocalDate dueDate,
        String currency,
        BigDecimal subtotal,
        BigDecimal taxTotal,
        BigDecimal grandTotal,
        Long postedTransactionId,
        boolean overdue,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static InvoiceResponse from(Invoice invoice, String contactName, InvoiceTotals totals) {
        boolean overdue = invoice.getStatus() == InvoiceStatus.SENT && invoice.getDueDate().isBefore(LocalDate.now());
        return new InvoiceResponse(
                invoice.getId(),
                invoice.getContactId(),
                contactName,
                invoice.getInvoiceNumber(),
                invoice.getStatus(),
                invoice.getIssueDate(),
                invoice.getDueDate(),
                invoice.getCurrency(),
                totals.subtotal(),
                totals.taxTotal(),
                totals.grandTotal(),
                invoice.getPostedTransactionId(),
                overdue,
                invoice.getCreatedAt(),
                invoice.getUpdatedAt());
    }
}
