package com.ledgerflow.service;

/** The editable shape of a bank account, used for both create and update. */
public record BankAccountDraft(Long accountId, String name, String accountNumberLast4) {}
