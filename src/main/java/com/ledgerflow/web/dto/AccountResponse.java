package com.ledgerflow.web.dto;

import com.ledgerflow.domain.Account;
import com.ledgerflow.domain.AccountType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record AccountResponse(
        Long id,
        String name,
        AccountType type,
        String currency,
        BigDecimal balance,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getName(),
                account.getType(),
                account.getCurrency(),
                account.getBalance(),
                account.getCreatedAt(),
                account.getUpdatedAt());
    }
}
