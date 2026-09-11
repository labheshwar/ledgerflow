package com.ledgerflow.web.dto;

import com.ledgerflow.domain.Account;
import java.math.BigDecimal;

public record BalanceResponse(Long accountId, BigDecimal balance, String currency) {

    public static BalanceResponse from(Account account) {
        return new BalanceResponse(account.getId(), account.getBalance(), account.getCurrency());
    }
}
