package com.ledgerflow.exception;

import java.util.List;

public class AccountNotFoundException extends RuntimeException {

    public AccountNotFoundException(List<Long> missingAccountIds) {
        super("No account found for id(s): " + missingAccountIds);
    }

    public AccountNotFoundException(Long accountId) {
        this(List.of(accountId));
    }
}
