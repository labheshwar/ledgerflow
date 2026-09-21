package com.ledgerflow.web.dto;

import com.ledgerflow.domain.Bill;
import com.ledgerflow.domain.BillStatus;
import com.ledgerflow.service.InvoiceTotals;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * @param overdue derived from {@code status} and {@code dueDate} on every
 *        read, never stored -- a due date does not change, so there is
 *        nothing to keep in sync. "Paid" is not derivable yet: nothing
 *        records a payment against a bill until milestone 12.
 */
public record BillResponse(
        Long id,
        Long contactId,
        String contactName,
        String billNumber,
        String vendorReference,
        BillStatus status,
        LocalDate billDate,
        LocalDate dueDate,
        String currency,
        BigDecimal subtotal,
        BigDecimal taxTotal,
        BigDecimal grandTotal,
        Long postedTransactionId,
        boolean overdue,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static BillResponse from(Bill bill, String contactName, InvoiceTotals totals) {
        boolean overdue = bill.getStatus() == BillStatus.OPEN && bill.getDueDate().isBefore(LocalDate.now());
        return new BillResponse(
                bill.getId(),
                bill.getContactId(),
                contactName,
                bill.getBillNumber(),
                bill.getVendorReference(),
                bill.getStatus(),
                bill.getBillDate(),
                bill.getDueDate(),
                bill.getCurrency(),
                totals.subtotal(),
                totals.taxTotal(),
                totals.grandTotal(),
                bill.getPostedTransactionId(),
                overdue,
                bill.getCreatedAt(),
                bill.getUpdatedAt());
    }
}
