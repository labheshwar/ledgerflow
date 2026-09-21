package com.ledgerflow.web.dto;

import com.ledgerflow.domain.Invoice;
import com.ledgerflow.domain.InvoiceStatus;
import com.ledgerflow.service.InvoiceTotals;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * @param amountPaid summed from every non-voided payment allocated against
 *        this invoice, never stored -- see {@code PaymentAllocationRepository.amountPaidFor}.
 * @param paid derived from {@code amountPaid} reaching {@code grandTotal}, not a status of its own:
 *        an invoice stays SENT whether it is owed in full, in part, or not at all.
 * @param overdue derived from {@code status}, {@code paid} and {@code dueDate} on every
 *        read, never stored -- a fully paid invoice is not overdue no matter how old its due date is.
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
        BigDecimal amountPaid,
        BigDecimal balanceDue,
        boolean paid,
        Long postedTransactionId,
        boolean overdue,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static InvoiceResponse from(Invoice invoice, String contactName, InvoiceTotals totals, BigDecimal amountPaid) {
        BigDecimal balanceDue = totals.grandTotal().subtract(amountPaid);
        boolean paid = invoice.getStatus() == InvoiceStatus.SENT && balanceDue.signum() <= 0;
        boolean overdue = invoice.getStatus() == InvoiceStatus.SENT && !paid && invoice.getDueDate().isBefore(LocalDate.now());
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
                amountPaid,
                balanceDue,
                paid,
                invoice.getPostedTransactionId(),
                overdue,
                invoice.getCreatedAt(),
                invoice.getUpdatedAt());
    }
}
