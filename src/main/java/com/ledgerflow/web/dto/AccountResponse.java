package com.ledgerflow.web.dto;

import com.ledgerflow.domain.Account;
import java.math.BigDecimal;

public record AccountResponse(Long id, String name, String currency, BigDecimal balance) {

    public static AccountResponse from(Account account) {
        return new AccountResponse(account.getId(), account.getName(), account.getCurrency(), account.getBalance());
    }
}
