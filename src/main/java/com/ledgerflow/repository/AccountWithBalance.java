package com.ledgerflow.repository;

import com.ledgerflow.domain.AccountType;
import com.ledgerflow.domain.SystemAccountRole;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * An account and what it is worth, as one read.
 *
 * Deliberately not the Account entity with a balance bolted on. The balance
 * is not a property of the row -- it is computed from the entries at the
 * moment of asking -- and giving Account a transient balance field would
 * invite exactly the confusion the change set out to remove: code that reads
 * a stale value off a detached entity and trusts it.
 *
 * @param balance in the account's own currency
 * @param baseBalance the same amount in the organization's reporting currency
 * @param rollupBalance this account's balance plus every descendant's, which
 *        is the only meaningful figure for a heading
 */
public record AccountWithBalance(
        Long id,
        String code,
        String name,
        String description,
        AccountType type,
        String currency,
        Long parentId,
        SystemAccountRole systemRole,
        boolean postable,
        OffsetDateTime archivedAt,
        BigDecimal balance,
        BigDecimal baseBalance,
        BigDecimal rollupBalance,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public boolean archived() {
        return archivedAt != null;
    }

    /**
     * A plain postable account with a balance -- the shape most tests mean
     * when they say "an account", without restating every structural field
     * they do not care about.
     */
    public static AccountWithBalance of(Long id, String code, AccountType type, BigDecimal balance) {
        return new AccountWithBalance(
                id, code, "Account " + code, null, type, "USD", null, null, true, null, balance, balance, balance,
                null, null);
    }

    /** A copy with the subtree total filled in. */
    public AccountWithBalance withRollup(BigDecimal rollup) {
        return new AccountWithBalance(
                id,
                code,
                name,
                description,
                type,
                currency,
                parentId,
                systemRole,
                postable,
                archivedAt,
                balance,
                baseBalance,
                rollup,
                createdAt,
                updatedAt);
    }
}
