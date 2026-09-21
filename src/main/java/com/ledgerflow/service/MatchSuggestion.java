package com.ledgerflow.service;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One candidate the reconciliation workspace offers for a statement line --
 * an existing ledger entry it could be, or an open invoice or bill it could
 * settle. {@code amount} is signed the same way a statement line reads, so
 * the two can be compared or displayed side by side without the caller
 * having to know which kind it is looking at.
 */
public record MatchSuggestion(MatchKind kind, Long id, String label, LocalDate date, BigDecimal amount, double score) {}
