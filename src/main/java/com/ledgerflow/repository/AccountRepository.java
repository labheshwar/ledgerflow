package com.ledgerflow.repository;

import com.ledgerflow.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Accounts as entities, for the write side. Reading an account together with
 * its balance goes through {@link AccountBalanceQueries} instead, because a
 * balance is a lateral join over the entries rather than a column on this row.
 */
@Transactional(readOnly = true)
public interface AccountRepository extends JpaRepository<Account, Long> {}
