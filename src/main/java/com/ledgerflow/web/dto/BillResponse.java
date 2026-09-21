package com.ledgerflow.web.dto;

import com.ledgerflow.domain.Bill;
import com.ledgerflow.domain.BillStatus;
import com.ledgerflow.service.InvoiceTotals;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * @param amountPaid summed from every non-voided payment allocated against
 *        this bill, never stored -- see {@code PaymentAllocationRepository.amountPaidFor}.
 * @param paid derived from {@code amountPaid} reaching {@code grandTotal}, not a status of its own.
 * @param overdue derived from {@code status}, {@code paid} and {@code dueDate} on every read, never stored.
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
        BigDecimal amountPaid,
        BigDecimal balanceDue,
        boolean paid,
        Long postedTransactionId,
        boolean overdue,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static BillResponse from(Bill bill, String contactName, InvoiceTotals totals, BigDecimal amountPaid) {
        BigDecimal balanceDue = totals.grandTotal().subtract(amountPaid);
        boolean paid = bill.getStatus() == BillStatus.OPEN && balanceDue.signum() <= 0;
        boolean overdue = bill.getStatus() == BillStatus.OPEN && !paid && bill.getDueDate().isBefore(LocalDate.now());
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
                amountPaid,
                balanceDue,
                paid,
                bill.getPostedTransactionId(),
                overdue,
                bill.getCreatedAt(),
                bill.getUpdatedAt());
    }
}
