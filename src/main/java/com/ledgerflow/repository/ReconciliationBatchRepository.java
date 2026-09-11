package com.ledgerflow.repository;

import com.ledgerflow.domain.ReconciliationBatch;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReconciliationBatchRepository extends JpaRepository<ReconciliationBatch, Long> {

    List<ReconciliationBatch> findAllByOrderByTriggeredAtDesc();

    Optional<ReconciliationBatch> findFirstByOrderByTriggeredAtDesc();
}
