package com.ledgerflow.repository;

import com.ledgerflow.domain.AccountType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * An account and what it is worth, as one read.
 *
 * Deliberately not the Account entity with a balance bolted on. The balance
 * is not a property of the row any more -- it is computed from the entries at
 * the moment of asking -- and giving Account a transient balance field would
 * invite exactly the confusion the migration set out to remove: code that
 * reads a stale value off a detached entity and trusts it.
 *
 * @param balance in the account's own currency
 * @param baseBalance the same amount in the organization's reporting currency
 */
public record AccountWithBalance(
        Long id,
        String name,
        AccountType type,
        String currency,
        BigDecimal balance,
        BigDecimal baseBalance,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {}
