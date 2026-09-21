package com.ledgerflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class MatchScoringTest {

    @Test
    void descriptionsThatShareEveryWordScorePerfectly() {
        double score = MatchScoring.descriptionSimilarity("Acme Corp", "ACME CORP");
        assertThat(score).isCloseTo(1.0, within(0.001));
    }

    @Test
    void descriptionsThatShareNoWordsScoreZero() {
        assertThat(MatchScoring.descriptionSimilarity("Coffee shop", "Electric utility")).isZero();
    }

    @Test
    void aBlankDescriptionNeverMatchesAnything() {
        assertThat(MatchScoring.descriptionSimilarity(null, "Acme Corp")).isZero();
        assertThat(MatchScoring.descriptionSimilarity("", "Acme Corp")).isZero();
    }

    @Test
    void partialWordOverlapScoresBetweenZeroAndOne() {
        // "acme", "corp" shared; "payment", "received", "from" only on one side,
        // "invoice", "1001" only on the other -- 2 shared out of 7 distinct tokens.
        double score = MatchScoring.descriptionSimilarity("Payment received from Acme Corp", "Acme Corp Invoice 1001");
        assertThat(score).isCloseTo(2.0 / 7.0, within(0.001));
    }

    @Test
    void theSameDateScoresPerfectly() {
        LocalDate date = LocalDate.of(2026, 1, 15);
        assertThat(MatchScoring.dateProximity(date, date, 10)).isEqualTo(1.0);
    }

    @Test
    void dateProximityFadesLinearlyAndFloorsAtZeroOutsideTheWindow() {
        LocalDate anchor = LocalDate.of(2026, 1, 15);
        assertThat(MatchScoring.dateProximity(anchor, anchor.plusDays(5), 10)).isCloseTo(0.5, within(0.001));
        assertThat(MatchScoring.dateProximity(anchor, anchor.plusDays(10), 10)).isZero();
        assertThat(MatchScoring.dateProximity(anchor, anchor.plusDays(30), 10)).isZero();
    }

    @Test
    void identicalAmountsScorePerfectly() {
        assertThat(MatchScoring.amountProximity(new BigDecimal("100.00"), new BigDecimal("100.00"))).isEqualTo(1.0);
    }

    @Test
    void amountsThatDivergeScoreLowerTheFartherApartTheyAre() {
        double closeScore = MatchScoring.amountProximity(new BigDecimal("100.00"), new BigDecimal("95.00"));
        double farScore = MatchScoring.amountProximity(new BigDecimal("100.00"), new BigDecimal("10.00"));

        assertThat(closeScore).isLessThan(1.0).isGreaterThan(farScore);
        assertThat(farScore).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    void bothAmountsZeroScoresPerfectlyRatherThanDividingByZero() {
        assertThat(MatchScoring.amountProximity(BigDecimal.ZERO, BigDecimal.ZERO)).isEqualTo(1.0);
    }
}
