package com.ledgerflow.repository;

import com.ledgerflow.domain.Account;
import com.ledgerflow.domain.AccountType;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<Account, Long> {

    List<Account> findAllByOrderByNameAsc();

    /**
     * A blank term matches everything, so the caller never has to branch.
     * Note the normalization below: binding a null into LOWER() leaves
     * Postgres unable to infer the parameter's type and it rejects the whole
     * statement with "function lower(bytea) does not exist".
     */
    default Page<Account> search(String q, AccountType type, Pageable pageable) {
        return searchFiltered(q == null ? "" : q.trim(), type, pageable);
    }

    @Query("""
            SELECT a FROM Account a
            WHERE (:type IS NULL OR a.type = :type)
              AND (:q = '' OR LOWER(a.name) LIKE LOWER(CONCAT('%', :q, '%')))
            """)
    Page<Account> searchFiltered(@Param("q") String q, @Param("type") AccountType type, Pageable pageable);
}
