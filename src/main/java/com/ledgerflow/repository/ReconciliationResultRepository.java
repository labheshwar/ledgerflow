package com.ledgerflow.repository;

import com.ledgerflow.domain.ReconciliationResult;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReconciliationResultRepository extends JpaRepository<ReconciliationResult, Long> {

    /**
     * Fetches the account eagerly (see EntryRepository.findByTransactionId
     * for why) -- ReconciliationResultResponse reads the account's name,
     * not just its ID.
     */
    @Query("SELECT r FROM ReconciliationResult r JOIN FETCH r.account WHERE r.batch.id = :batchId")
    List<ReconciliationResult> findByBatchId(@Param("batchId") Long batchId);

    /**
     * The list view only needs matched/mismatched tallies, so it reads them in
     * one grouped query rather than fetching every result row for every batch
     * on the page.
     */
    @Query("""
            SELECT new com.ledgerflow.repository.BatchStatusCount(r.batch.id, r.status, COUNT(r))
            FROM ReconciliationResult r
            WHERE r.batch.id IN :batchIds
            GROUP BY r.batch.id, r.status
            """)
    List<BatchStatusCount> countByStatusForBatches(@Param("batchIds") Collection<Long> batchIds);
}
