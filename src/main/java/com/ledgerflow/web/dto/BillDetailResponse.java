package com.ledgerflow.web.dto;

import com.ledgerflow.domain.Bill;
import com.ledgerflow.domain.BillLine;
import com.ledgerflow.domain.BillStatus;
import com.ledgerflow.service.InvoiceTotals;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.IntStream;

public record BillDetailResponse(
        Long id,
        Long contactId,
        String contactName,
        String billNumber,
        String vendorReference,
        BillStatus status,
        LocalDate billDate,
        LocalDate dueDate,
        String currency,
        String notes,
        BigDecimal subtotal,
        BigDecimal taxTotal,
        BigDecimal grandTotal,
        Long postedTransactionId,
        boolean overdue,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<BillLineResponse> lines) {

    public static BillDetailResponse from(Bill bill, String contactName, List<BillLine> lines, InvoiceTotals totals) {
        boolean overdue = bill.getStatus() == BillStatus.OPEN && bill.getDueDate().isBefore(LocalDate.now());
        List<BillLineResponse> lineResponses = IntStream.range(0, lines.size())
                .mapToObj(i -> BillLineResponse.from(lines.get(i), totals.lines().get(i)))
                .toList();
        return new BillDetailResponse(
                bill.getId(),
                bill.getContactId(),
                contactName,
                bill.getBillNumber(),
                bill.getVendorReference(),
                bill.getStatus(),
                bill.getBillDate(),
                bill.getDueDate(),
                bill.getCurrency(),
                bill.getNotes(),
                totals.subtotal(),
                totals.taxTotal(),
                totals.grandTotal(),
                bill.getPostedTransactionId(),
                overdue,
                bill.getCreatedAt(),
                bill.getUpdatedAt(),
                lineResponses);
    }
}
