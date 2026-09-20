package com.ledgerflow.service;

import com.ledgerflow.domain.AccountType;
import com.ledgerflow.domain.SystemAccountRole;

/**
 * The editable shape of an account, used for both create and update.
 *
 * One record for both because the rules differ by what the account already
 * *is*, not by which endpoint was called: a brand-new account and one that has
 * never been posted to are equally free to change, and one with entries
 * against it is equally constrained either way.
 *
 * @param currency null means the organization's reporting currency
 * @param postable false makes this a heading rather than something entries
 *        can land on
 */
public record AccountDraft(
        String code,
        String name,
        String description,
        AccountType type,
        String currency,
        Long parentId,
        SystemAccountRole systemRole,
        boolean postable) {}
