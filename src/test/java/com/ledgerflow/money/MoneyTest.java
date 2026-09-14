package com.ledgerflow.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MoneyTest {

    @Test
    void normalizesToTheCurrencysScale() {
        assertThat(Money.of("40", "USD").amount()).isEqualByComparingTo("40.00");
        assertThat(Money.of("40", "USD").amount().scale()).isEqualTo(2);
        // Yen has no minor unit, so a scale of 2 would be a lie.
        assertThat(Money.of("40", "JPY").amount().scale()).isZero();
    }

    @Test
    void refusesAnAmountTheCurrencyCannotRepresent() {
        // Silently rounding here is how a ledger loses a hundredth at a time.
        assertThatThrownBy(() -> Money.of("10.005", "USD"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a representable amount");

        assertThatThrownBy(() -> Money.of("10.5", "JPY")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refusesCurrenciesThatAreNotMoney() {
        assertThatThrownBy(() -> Money.of("1", "BTC")).isInstanceOf(IllegalArgumentException.class);
        // XAU is gold: a real ISO code, but it reports no minor unit.
        assertThatThrownBy(() -> Money.of("1", "XAU"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no minor unit");
    }

    @Test
    void addingAcrossCurrenciesIsRefusedRatherThanGuessed() {
        Money dollars = Money.of("10.00", "USD");
        Money euros = Money.of("10.00", "EUR");

        assertThatThrownBy(() -> dollars.plus(euros))
                .isInstanceOf(CurrencyMismatchException.class)
                .hasMessageContaining("USD")
                .hasMessageContaining("EUR");

        // Comparison too -- ordering by a meaningless number is no better
        // than adding one.
        assertThatThrownBy(() -> dollars.compareTo(euros)).isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    void arithmeticStaysExact() {
        assertThat(Money.of("0.10", "USD").plus(Money.of("0.20", "USD")))
                .isEqualTo(Money.of("0.30", "USD"));
        // The canonical float failure: 0.1 + 0.2 == 0.30000000000000004.
        assertThat(Money.of("0.10", "USD").plus(Money.of("0.20", "USD")).amount())
                .isEqualByComparingTo(new BigDecimal("0.30"));

        assertThat(Money.of("5.00", "USD").minus(Money.of("7.50", "USD")))
                .isEqualTo(Money.of("-2.50", "USD"));
        assertThat(Money.of("-2.50", "USD").negated()).isEqualTo(Money.of("2.50", "USD"));
    }

    @Test
    void equalityIgnoresHowTheAmountWasWritten() {
        // A record's generated equals uses BigDecimal.equals, which considers
        // 40.0 and 40.00 different. Normalizing in the constructor is what
        // makes these the same value.
        assertThat(Money.of("40", "USD")).isEqualTo(Money.of("40.00", "USD"));
        assertThat(Money.of("40", "usd")).isEqualTo(Money.of("40.00", "USD"));
        assertThat(Money.of("40", "USD")).hasSameHashCodeAs(Money.of("40.0000", "USD"));
    }

    @Test
    void splittingAThreeWayCentDoesNotLoseIt() {
        List<Money> parts = Money.of("0.05", "USD").allocateEvenly(3);

        // Not 0.0166... each, and not 0.02/0.02/0.02 either.
        assertThat(parts)
                .containsExactly(Money.of("0.02", "USD"), Money.of("0.02", "USD"), Money.of("0.01", "USD"));
        assertThat(sum(parts)).isEqualTo(Money.of("0.05", "USD"));
    }

    @Test
    void splittingByWeightGivesTheRemainderToTheLargestShortfall() {
        List<Money> parts = Money.of("100.00", "USD").allocate(3, 3, 3);
        assertThat(parts)
                .containsExactly(Money.of("33.34", "USD"), Money.of("33.33", "USD"), Money.of("33.33", "USD"));
        assertThat(sum(parts)).isEqualTo(Money.of("100.00", "USD"));
    }

    @Test
    void splittingANegativeAmountStillReconciles() {
        // Refunds and reversals are negative; the leftover has to move the
        // other way or the parts overshoot.
        List<Money> parts = Money.of("-0.05", "USD").allocateEvenly(3);
        assertThat(sum(parts)).isEqualTo(Money.of("-0.05", "USD"));
        assertThat(parts)
                .containsExactly(Money.of("-0.02", "USD"), Money.of("-0.02", "USD"), Money.of("-0.01", "USD"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.01", "0.05", "1.00", "99.99", "12345.67", "-7.03"})
    void everySplitSumsBackToTheOriginal(String amount) {
        Money original = Money.of(amount, "USD");
        for (int parts = 1; parts <= 7; parts++) {
            assertThat(sum(original.allocateEvenly(parts)))
                    .as("splitting %s into %d", amount, parts)
                    .isEqualTo(original);
        }
        assertThat(sum(original.allocate(1, 2, 3, 5))).isEqualTo(original);
        assertThat(sum(original.allocate(0, 1))).isEqualTo(original);
    }

    @Test
    void convertsAtAnExplicitRateAndRoundsToTheTargetCurrency() {
        Money euros = Money.of("100.00", "EUR");

        assertThat(euros.convertedTo("USD", new BigDecimal("1.0825"))).isEqualTo(Money.of("108.25", "USD"));
        // Rounds to whole yen, because there is no such thing as a tenth of one.
        assertThat(euros.convertedTo("JPY", new BigDecimal("171.234"))).isEqualTo(Money.of("17123", "JPY"));

        assertThatThrownBy(() -> euros.convertedTo("USD", BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void convertsToMinorUnitsForTheWire() {
        assertThat(Money.of("12.34", "USD").toMinorUnits()).isEqualTo(1234);
        assertThat(Money.of("-12.34", "USD").toMinorUnits()).isEqualTo(-1234);
        assertThat(Money.of("1234", "JPY").toMinorUnits()).isEqualTo(1234);
        assertThat(Money.ofMinorUnits(1234, "USD")).isEqualTo(Money.of("12.34", "USD"));
    }

    private Money sum(List<Money> parts) {
        return parts.stream().reduce(Money.zero(parts.get(0).currency()), Money::plus);
    }
}
