package com.ledgerflow.service;

import com.ledgerflow.domain.Account;
import com.ledgerflow.exception.AccountNotFoundException;
import com.ledgerflow.repository.AccountRepository;
import org.springframework.stereotype.Service;

@Service
public class BalanceService {

    private final AccountRepository accountRepository;

    public BalanceService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public Account getAccount(Long accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
    }
}
