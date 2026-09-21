package com.ledgerflow.repository;

import com.ledgerflow.domain.BankAccount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
public interface BankAccountRepository extends JpaRepository<BankAccount, Long> {

    default Page<BankAccount> search(String q, boolean includeArchived, Pageable pageable) {
        return searchFiltered(q == null ? "" : q.trim(), includeArchived, pageable);
    }

    @Query("""
            SELECT b FROM BankAccount b
            WHERE (:includeArchived = true OR b.archivedAt IS NULL)
              AND (:q = '' OR LOWER(b.name) LIKE LOWER(CONCAT('%', :q, '%')))
            """)
    Page<BankAccount> searchFiltered(
            @Param("q") String q, @Param("includeArchived") boolean includeArchived, Pageable pageable);
}
