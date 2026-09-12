package com.ledgerflow.repository;

import com.ledgerflow.domain.AuditLog;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findTop200ByOrderByCreatedAtDesc();

    /** Blank matches everything; see AccountRepository.search for why null is normalized away. */
    default Page<AuditLog> search(String q, String entityType, Pageable pageable) {
        return searchFiltered(q == null ? "" : q.trim(), entityType, pageable);
    }

    @Query("""
            SELECT a FROM AuditLog a
            WHERE (:entityType IS NULL OR a.entityType = :entityType)
              AND (:q = ''
                   OR LOWER(a.actor) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(a.action) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(a.entityType) LIKE LOWER(CONCAT('%', :q, '%')))
            """)
    Page<AuditLog> searchFiltered(
            @Param("q") String q, @Param("entityType") String entityType, Pageable pageable);
}
