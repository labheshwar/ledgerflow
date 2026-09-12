package com.ledgerflow.repository;

import com.ledgerflow.domain.ReconciliationBatch;
import com.ledgerflow.domain.ReconciliationStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReconciliationBatchRepository extends JpaRepository<ReconciliationBatch, Long> {

    List<ReconciliationBatch> findAllByOrderByTriggeredAtDesc();

    Optional<ReconciliationBatch> findFirstByOrderByTriggeredAtDesc();

    @Query("SELECT b FROM ReconciliationBatch b WHERE :status IS NULL OR b.status = :status")
    Page<ReconciliationBatch> search(@Param("status") ReconciliationStatus status, Pageable pageable);
}
