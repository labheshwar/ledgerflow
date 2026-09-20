package com.ledgerflow.repository;

import com.ledgerflow.domain.Account;
import com.ledgerflow.domain.SystemAccountRole;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Accounts as entities, for the write side and for the chart's own structure.
 * Reading an account together with its balance goes through
 * {@link AccountBalanceQueries} instead, because a balance is a lateral join
 * over the entries rather than a column on this row.
 */
@Transactional(readOnly = true)
public interface AccountRepository extends JpaRepository<Account, Long> {

    /**
     * Row-level security scopes this to the current organization, which is
     * what makes a lookup by role safe: there is exactly one per organization,
     * and this cannot return another tenant's.
     */
    Optional<Account> findBySystemRole(SystemAccountRole systemRole);

    List<Account> findByParentId(Long parentId);
}
