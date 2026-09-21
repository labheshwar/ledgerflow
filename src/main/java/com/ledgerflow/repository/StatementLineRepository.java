package com.ledgerflow.repository;

import com.ledgerflow.domain.StatementLine;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
public interface StatementLineRepository extends JpaRepository<StatementLine, Long> {

    /** Preview rows while {@code committed} is false, the statement itself once true. */
    Page<StatementLine> findByImportIdAndCommittedOrderByTxnDateAsc(Long importId, boolean committed, Pageable pageable);

    Page<StatementLine> findByBankAccountIdAndCommittedOrderByTxnDateDesc(
            Long bankAccountId, boolean committed, Pageable pageable);

    /** The reconciliation workspace's own two panes: what still needs matching, and what already has been. */
    Page<StatementLine> findByBankAccountIdAndCommittedTrueAndMatchedEntryIdIsNullOrderByTxnDateDesc(
            Long bankAccountId, Pageable pageable);

    Page<StatementLine> findByBankAccountIdAndCommittedTrueAndMatchedEntryIdIsNotNullOrderByTxnDateDesc(
            Long bankAccountId, Pageable pageable);

    long countByBankAccountIdAndCommittedTrue(Long bankAccountId);

    long countByBankAccountIdAndCommittedTrueAndMatchedEntryIdIsNotNull(Long bankAccountId);

    /** The one-line-per-entry guard's own read, ahead of relying on the partial unique index to catch a race. */
    boolean existsByMatchedEntryId(Long matchedEntryId);

    /**
     * The dedupe check itself. Only ever true for a row from an import that
     * was actually committed -- an abandoned or re-previewed import's own
     * staged rows must not block a later import from claiming the same
     * external id, which is exactly what the partial unique index this
     * mirrors (committed rows only) also enforces at the database level.
     */
    boolean existsByBankAccountIdAndExternalIdAndCommittedTrue(Long bankAccountId, String externalId);

    /** Clears a prior preview attempt before re-parsing with a changed mapping. */
    @Modifying
    @Transactional
    void deleteByImportIdAndCommittedFalse(Long importId);

    /** The commit step itself -- one statement, so a conflict rolls back the whole import rather than half-committing it. */
    @Modifying
    @Transactional
    @Query("UPDATE StatementLine s SET s.committed = true WHERE s.importId = :importId AND s.committed = false")
    int commitByImportId(@Param("importId") Long importId);
}
