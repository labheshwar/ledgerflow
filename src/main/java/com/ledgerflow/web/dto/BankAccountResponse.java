package com.ledgerflow.web.dto;

import com.ledgerflow.domain.BankAccount;
import java.time.OffsetDateTime;

public record BankAccountResponse(
        Long id,
        Long accountId,
        String accountName,
        String name,
        String accountNumberLast4,
        String currency,
        boolean archived,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static BankAccountResponse from(BankAccount bankAccount, String accountName) {
        return new BankAccountResponse(
                bankAccount.getId(),
                bankAccount.getAccountId(),
                accountName,
                bankAccount.getName(),
                bankAccount.getAccountNumberLast4(),
                bankAccount.getCurrency(),
                bankAccount.isArchived(),
                bankAccount.getCreatedAt(),
                bankAccount.getUpdatedAt());
    }
}
