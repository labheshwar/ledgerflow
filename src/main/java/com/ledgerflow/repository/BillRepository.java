package com.ledgerflow.repository;

import com.ledgerflow.domain.Bill;
import com.ledgerflow.domain.BillStatus;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
public interface BillRepository extends JpaRepository<Bill, Long> {

    /** Blank matches everything; see AccountRepository.search for why null is normalized away first. */
    default Page<Bill> search(String q, BillStatus status, Pageable pageable) {
        return searchFiltered(q == null ? "" : q.trim(), status, pageable);
    }

    @Query("""
            SELECT b FROM Bill b JOIN Contact c ON c.id = b.contactId
            WHERE (:status IS NULL OR b.status = :status)
              AND (:q = ''
                   OR LOWER(b.billNumber) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(b.vendorReference) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(c.name) LIKE LOWER(CONCAT('%', :q, '%')))
            """)
    Page<Bill> searchFiltered(@Param("q") String q, @Param("status") BillStatus status, Pageable pageable);

    /**
     * Same guard {@code InvoiceRepository} has, for the same reason: a crash
     * between drawing the number and recording {@code posted_transaction_id}
     * leaves a bill OPEN with nothing posted yet. {@link
     * com.ledgerflow.service.BillSweeper} retries these.
     */
    List<Bill> findByStatusAndPostedTransactionIdIsNull(BillStatus status);

    /** The duplicate-vendor-bill guard's own read -- see {@code Bill}'s Javadoc on {@code vendorReference}. */
    List<Bill> findByContactIdAndVendorReference(Long contactId, String vendorReference);

    /** Candidates for a payment's allocation picker -- only ever a specific contact's own open bills. */
    List<Bill> findByContactIdAndStatusOrderByDueDateAsc(Long contactId, BillStatus status);

    /** The reconciliation workspace's own candidate list -- every open bill, not just one contact's. */
    List<Bill> findByStatusOrderByDueDateAsc(BillStatus status);
}
