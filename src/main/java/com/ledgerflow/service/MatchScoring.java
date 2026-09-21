package com.ledgerflow.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The fuzzy half of the reconciliation workspace's matching -- everything
 * here is a pure function of the values in front of it, kept apart from
 * {@link ReconciliationService} so the scoring itself can be tested without
 * a database.
 *
 * None of this decides whether two things match; it only ranks candidates
 * that some harder rule (an exact amount, an open balance) has already let
 * through. A score is a suggestion, not a verdict -- the workspace always
 * leaves the actual match to whoever is looking at the two-pane view.
 */
final class MatchScoring {

    private MatchScoring() {}

    /**
     * Token overlap (Jaccard) between two free-text descriptions, 0 to 1.
     * Not edit distance: a bank's own text and a contact's name rarely
     * share a common substring longer than a word, but they do tend to
     * share the words themselves -- "ACME CORP" against "Payment received
     * from Acme Corp" overlaps completely on tokens and barely at all
     * character-by-character.
     */
    static double descriptionSimilarity(String a, String b) {
        Set<String> tokensA = tokenize(a);
        Set<String> tokensB = tokenize(b);
        if (tokensA.isEmpty() || tokensB.isEmpty()) {
            return 0.0;
        }
        Set<String> intersection = new HashSet<>(tokensA);
        intersection.retainAll(tokensB);
        Set<String> union = new HashSet<>(tokensA);
        union.addAll(tokensB);
        return (double) intersection.size() / union.size();
    }

    /** 1.0 for the same day, fading to 0 at {@code toleranceDays} apart or more. */
    static double dateProximity(LocalDate a, LocalDate b, int toleranceDays) {
        long diff = Math.abs(ChronoUnit.DAYS.between(a, b));
        if (diff >= toleranceDays) {
            return 0.0;
        }
        return 1.0 - ((double) diff / toleranceDays);
    }

    /** 1.0 for identical amounts, fading toward 0 as they diverge relative to the larger of the two. */
    static double amountProximity(BigDecimal a, BigDecimal b) {
        BigDecimal larger = a.abs().max(b.abs());
        if (larger.signum() == 0) {
            return 1.0;
        }
        BigDecimal difference = a.subtract(b).abs();
        double ratio = difference.divide(larger, java.math.MathContext.DECIMAL64).doubleValue();
        return Math.max(0.0, 1.0 - ratio);
    }

    private static Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+"))
                .filter(token -> token.length() > 1)
                .collect(Collectors.toSet());
    }
}
