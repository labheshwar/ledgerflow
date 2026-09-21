package com.ledgerflow.repository;

import com.ledgerflow.domain.StatementImport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
public interface StatementImportRepository extends JpaRepository<StatementImport, Long> {

    Page<StatementImport> findByBankAccountIdOrderByCreatedAtDesc(Long bankAccountId, Pageable pageable);
}
