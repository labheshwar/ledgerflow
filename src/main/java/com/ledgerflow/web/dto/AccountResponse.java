package com.ledgerflow.web.dto;

import com.ledgerflow.domain.AccountType;
import com.ledgerflow.domain.SystemAccountRole;
import com.ledgerflow.repository.AccountWithBalance;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * @param balance derived from the entries at read time, not a stored column
 * @param baseBalance the same amount in the organization's reporting currency
 * @param rollupBalance this account plus everything beneath it
 * @param postable false for a heading, which cannot receive entries
 * @param systemRole set only where the application itself needs this account
 */
public record AccountResponse(
        Long id,
        String code,
        String name,
        String description,
        AccountType type,
        String currency,
        Long parentId,
        SystemAccountRole systemRole,
        boolean postable,
        boolean archived,
        BigDecimal balance,
        BigDecimal baseBalance,
        BigDecimal rollupBalance,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static AccountResponse from(AccountWithBalance account) {
        return new AccountResponse(
                account.id(),
                account.code(),
                account.name(),
                account.description(),
                account.type(),
                account.currency(),
                account.parentId(),
                account.systemRole(),
                account.postable(),
                account.archived(),
                account.balance(),
                account.baseBalance(),
                account.rollupBalance(),
                account.createdAt(),
                account.updatedAt());
    }
}
