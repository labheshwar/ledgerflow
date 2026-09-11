package com.ledgerflow.web.dto;

import com.ledgerflow.domain.Account;
import com.ledgerflow.domain.AccountType;
import java.math.BigDecimal;

public record AccountResponse(Long id, String name, AccountType type, String currency, BigDecimal balance) {

    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getId(), account.getName(), account.getType(), account.getCurrency(), account.getBalance());
    }
}
