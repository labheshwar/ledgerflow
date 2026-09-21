package com.ledgerflow.repository;

import com.ledgerflow.domain.Invoice;
import com.ledgerflow.domain.InvoiceStatus;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    /** Blank matches everything; see AccountRepository.search for why null is normalized away first. */
    default Page<Invoice> search(String q, InvoiceStatus status, Pageable pageable) {
        return searchFiltered(q == null ? "" : q.trim(), status, pageable);
    }

    @Query("""
            SELECT i FROM Invoice i JOIN Contact c ON c.id = i.contactId
            WHERE (:status IS NULL OR i.status = :status)
              AND (:q = ''
                   OR LOWER(i.invoiceNumber) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(c.name) LIKE LOWER(CONCAT('%', :q, '%')))
            """)
    Page<Invoice> searchFiltered(@Param("q") String q, @Param("status") InvoiceStatus status, Pageable pageable);

    /**
     * Sent but never finished posting -- a crash between drawing the number
     * and recording {@code posted_transaction_id}, or between posting and
     * that same write-back. {@link com.ledgerflow.service.InvoiceSweeper}
     * retries these; see the plan's own warning about the outer-transaction
     * trap for why sending is split into these two separately-committed
     * halves in the first place.
     */
    List<Invoice> findByStatusAndPostedTransactionIdIsNull(InvoiceStatus status);

    /** Candidates for a payment's allocation picker -- only ever a specific contact's own open invoices. */
    List<Invoice> findByContactIdAndStatusOrderByDueDateAsc(Long contactId, InvoiceStatus status);

    /** The reconciliation workspace's own candidate list -- every open invoice, not just one contact's. */
    List<Invoice> findByStatusOrderByDueDateAsc(InvoiceStatus status);
}
