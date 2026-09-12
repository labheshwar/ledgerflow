package com.ledgerflow.repository;

import com.ledgerflow.domain.Transaction;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    List<Transaction> findAllByOrderByCreatedAtDesc();

    long countByCreatedAtAfter(OffsetDateTime createdAt);

    /** Blank matches everything; see AccountRepository.search for why null is normalized away. */
    default Page<Transaction> search(String q, Pageable pageable) {
        return searchFiltered(q == null ? "" : q.trim(), pageable);
    }

    @Query("""
            SELECT t FROM Transaction t
            WHERE :q = ''
               OR LOWER(t.description) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(t.idempotencyKey) LIKE LOWER(CONCAT('%', :q, '%'))
            """)
    Page<Transaction> searchFiltered(@Param("q") String q, Pageable pageable);
}
