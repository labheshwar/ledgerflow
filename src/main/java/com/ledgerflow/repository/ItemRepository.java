package com.ledgerflow.repository;

import com.ledgerflow.domain.Item;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
public interface ItemRepository extends JpaRepository<Item, Long> {

    default Page<Item> search(String q, boolean includeArchived, Pageable pageable) {
        return searchFiltered(q == null ? "" : q.trim(), includeArchived, pageable);
    }

    @Query("""
            SELECT i FROM Item i
            WHERE (:includeArchived = true OR i.archivedAt IS NULL)
              AND (:q = ''
                   OR LOWER(i.name) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(i.sku) LIKE LOWER(CONCAT('%', :q, '%')))
            """)
    Page<Item> searchFiltered(
            @Param("q") String q, @Param("includeArchived") boolean includeArchived, Pageable pageable);
}
