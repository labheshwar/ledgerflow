package com.ledgerflow.repository;

import com.ledgerflow.domain.DocumentType;
import com.ledgerflow.domain.PaymentAllocation;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
public interface PaymentAllocationRepository extends JpaRepository<PaymentAllocation, Long> {

    List<PaymentAllocation> findByPaymentId(Long paymentId);

    List<PaymentAllocation> findByDocumentTypeAndDocumentIdOrderByIdAsc(DocumentType documentType, Long documentId);

    /**
     * How much of one invoice or bill has actually been settled -- a
     * voided payment's allocations do not count, exactly as a voided
     * invoice's posting is undone rather than merely hidden.
     */
    @Query("""
            SELECT COALESCE(SUM(a.amount), 0) FROM PaymentAllocation a JOIN Payment p ON p.id = a.paymentId
            WHERE a.documentType = :documentType AND a.documentId = :documentId AND p.status <> 'VOID'
            """)
    BigDecimal amountPaidFor(@Param("documentType") DocumentType documentType, @Param("documentId") Long documentId);
}
