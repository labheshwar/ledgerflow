package com.ledgerflow.repository;

import com.ledgerflow.domain.ReconciliationResult;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReconciliationResultRepository extends JpaRepository<ReconciliationResult, Long> {

    List<ReconciliationResult> findByBatchId(Long batchId);
}
