package com.ledgerflow.web.dto;

import com.ledgerflow.service.AccountBalance;
import java.math.BigDecimal;

public record BalanceResponse(Long accountId, BigDecimal balance, String currency) {

    public static BalanceResponse from(AccountBalance balance) {
        return new BalanceResponse(balance.accountId(), balance.balance(), balance.currency());
    }
}
