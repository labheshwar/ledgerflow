package com.ledgerflow.service;

/** How far through its own statement a bank account's reconciliation workspace has gotten. */
public record ReconciliationSummary(long totalLines, long matchedLines, long unmatchedLines) {}
