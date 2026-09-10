package com.ledgerflow.repository;

import com.ledgerflow.domain.ReconciliationBatch;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReconciliationBatchRepository extends JpaRepository<ReconciliationBatch, Long> {
}
