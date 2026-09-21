package com.ledgerflow.repository;

import com.ledgerflow.domain.Payment;
import com.ledgerflow.domain.PaymentDirection;
import com.ledgerflow.domain.PaymentStatus;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /** Blank matches everything; see AccountRepository.search for why null is normalized away first. */
    default Page<Payment> search(String q, PaymentDirection direction, Pageable pageable) {
        return searchFiltered(q == null ? "" : q.trim(), direction, pageable);
    }

    @Query("""
            SELECT p FROM Payment p JOIN Contact c ON c.id = p.contactId
            WHERE (:direction IS NULL OR p.direction = :direction)
              AND (:q = '' OR LOWER(c.name) LIKE LOWER(CONCAT('%', :q, '%')))
            """)
    Page<Payment> searchFiltered(@Param("q") String q, @Param("direction") PaymentDirection direction, Pageable pageable);

    /** Posted but never finished -- see {@code PaymentSweeper}. */
    List<Payment> findByStatusAndPostedTransactionIdIsNull(PaymentStatus status);
}
