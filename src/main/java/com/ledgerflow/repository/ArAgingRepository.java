package com.ledgerflow.repository;

import com.ledgerflow.domain.AgingEntryId;
import com.ledgerflow.domain.ArAgingEntry;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ArAgingRepository extends JpaRepository<ArAgingEntry, AgingEntryId> {

    /**
     * Explicit JPQL rather than method-name derivation: {@code orgId} is
     * one half of this entity's {@code @IdClass} composite key, and letting
     * Spring Data derive the query from the method name risks it being
     * resolved against the composite id property instead of the plain
     * column a simple equality filter needs here.
     */
    @Query("SELECT e FROM ArAgingEntry e WHERE e.orgId = :orgId ORDER BY e.dueDate ASC")
    List<ArAgingEntry> findByOrgIdOrderByDueDateAsc(@Param("orgId") Long orgId);

    @Modifying
    @Query("DELETE FROM ArAgingEntry e WHERE e.orgId = :orgId")
    void deleteByOrgId(@Param("orgId") Long orgId);
}
