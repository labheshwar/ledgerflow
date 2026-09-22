package com.ledgerflow.repository;

import com.ledgerflow.domain.ApAgingEntry;
import com.ledgerflow.domain.ApAgingEntryId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApAgingRepository extends JpaRepository<ApAgingEntry, ApAgingEntryId> {

    /** See {@link ArAgingRepository#findByOrgIdOrderByDueDateAsc} for why this is explicit JPQL, not a derived query. */
    @Query("SELECT e FROM ApAgingEntry e WHERE e.orgId = :orgId ORDER BY e.dueDate ASC")
    List<ApAgingEntry> findByOrgIdOrderByDueDateAsc(@Param("orgId") Long orgId);

    @Modifying
    @Query("DELETE FROM ApAgingEntry e WHERE e.orgId = :orgId")
    void deleteByOrgId(@Param("orgId") Long orgId);
}
