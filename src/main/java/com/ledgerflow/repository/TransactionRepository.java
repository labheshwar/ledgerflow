package com.ledgerflow.repository;

import com.ledgerflow.domain.Transaction;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    List<Transaction> findAllByOrderByCreatedAtDesc();

    long countByCreatedAtAfter(OffsetDateTime createdAt);
}
