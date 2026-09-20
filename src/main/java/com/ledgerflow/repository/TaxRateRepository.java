package com.ledgerflow.repository;

import com.ledgerflow.domain.TaxRate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
public interface TaxRateRepository extends JpaRepository<TaxRate, Long> {

    default Page<TaxRate> search(String q, boolean includeArchived, Pageable pageable) {
        return searchFiltered(q == null ? "" : q.trim(), includeArchived, pageable);
    }

    @Query("""
            SELECT t FROM TaxRate t
            WHERE (:includeArchived = true OR t.archivedAt IS NULL)
              AND (:q = '' OR LOWER(t.name) LIKE LOWER(CONCAT('%', :q, '%')))
            """)
    Page<TaxRate> searchFiltered(
            @Param("q") String q, @Param("includeArchived") boolean includeArchived, Pageable pageable);
}
