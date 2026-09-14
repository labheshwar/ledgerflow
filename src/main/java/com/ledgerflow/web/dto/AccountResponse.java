package com.ledgerflow.web.dto;

import com.ledgerflow.domain.AccountType;
import com.ledgerflow.repository.AccountWithBalance;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * @param balance derived from the entries at read time, not a stored column
 * @param baseBalance the same amount in the organization's reporting currency
 */
public record AccountResponse(
        Long id,
        String name,
        AccountType type,
        String currency,
        BigDecimal balance,
        BigDecimal baseBalance,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static AccountResponse from(AccountWithBalance account) {
        return new AccountResponse(
                account.id(),
                account.name(),
                account.type(),
                account.currency(),
                account.balance(),
                account.baseBalance(),
                account.createdAt(),
                account.updatedAt());
    }
}
