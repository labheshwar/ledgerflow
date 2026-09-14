package com.ledgerflow.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.IntStream;

/**
 * An amount of one currency.
 *
 * Hand-rolled rather than pulled from a library, because the behaviour worth
 * having is small and the behaviour worth refusing is the point. Three rules:
 *
 * 1. A bare BigDecimal cannot be added to another currency's BigDecimal
 *    without anyone noticing. A Money can only be added to Money of the same
 *    currency, so "dollars plus euros" stops being expressible.
 * 2. The scale is the currency's, not whatever the caller happened to type.
 *    JPY has no minor unit; USD has two. An amount of 10.005 USD does not
 *    exist, and quietly rounding it is how money goes missing a hundredth at
 *    a time, so constructing one throws.
 * 3. Splitting is exact. {@link #allocate} guarantees the parts sum back to
 *    the original -- the thing naive division never does.
 *
 * Never float. A double cannot represent 0.10, so a ledger built on doubles
 * disagrees with itself by cents that nobody can account for.
 */
public record Money(BigDecimal amount, String currency) implements Comparable<Money> {

    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        currency = currency.toUpperCase(Locale.ROOT);

        int digits = fractionDigits(currency);
        try {
            amount = amount.setScale(digits, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException(
                    "%s is not a representable amount in %s, which has %d decimal place%s"
                            .formatted(amount.toPlainString(), currency, digits, digits == 1 ? "" : "s"));
        }
    }

    public static Money of(BigDecimal amount, String currency) {
        return new Money(amount, currency);
    }

    /** Parses an exact decimal string. Deliberately not a double overload. */
    public static Money of(String amount, String currency) {
        return new Money(new BigDecimal(amount), currency);
    }

    public static Money zero(String currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.subtract(other.amount), currency);
    }

    public Money negated() {
        return new Money(amount.negate(), currency);
    }

    public Money abs() {
        return new Money(amount.abs(), currency);
    }

    /**
     * Converts at an explicit rate. The rate is always passed in rather than
     * looked up, because the rate that matters is the one that applied when
     * the transaction happened, not the one in effect when someone runs a
     * report two years later.
     */
    public Money convertedTo(String targetCurrency, BigDecimal rate) {
        Objects.requireNonNull(rate, "rate");
        if (rate.signum() <= 0) {
            throw new IllegalArgumentException("An exchange rate must be positive, got " + rate.toPlainString());
        }
        BigDecimal converted =
                amount.multiply(rate).setScale(fractionDigits(targetCurrency), RoundingMode.HALF_EVEN);
        return new Money(converted, targetCurrency);
    }

    /**
     * Splits this amount into parts proportional to the weights, using the
     * largest-remainder method, such that the parts sum back to exactly this
     * amount.
     *
     * Dividing money is not dividing numbers. 0.05 split three ways is not
     * three parts of 0.016667 -- it is 0.02, 0.02, 0.01, because a cent
     * cannot be cut. Rounding each share independently loses or invents
     * money; here the truncated shares are handed out first and the leftover
     * minor units go one each to whoever was rounded down hardest. That is
     * the fairest distribution for which the total still reconciles, and a
     * total that does not reconcile is an unbalanced journal entry.
     */
    public List<Money> allocate(int... weights) {
        if (weights.length == 0) {
            throw new IllegalArgumentException("Cannot allocate across zero parts");
        }
        long totalWeight = 0;
        for (int weight : weights) {
            if (weight < 0) {
                throw new IllegalArgumentException("Allocation weights cannot be negative");
            }
            totalWeight += weight;
        }
        if (totalWeight == 0) {
            throw new IllegalArgumentException("Allocation weights must not all be zero");
        }

        long minorUnits = toMinorUnits();
        long[] shares = new long[weights.length];
        long[] remainders = new long[weights.length];
        long distributed = 0;

        for (int i = 0; i < weights.length; i++) {
            long numerator = Math.multiplyExact(minorUnits, weights[i]);
            shares[i] = numerator / totalWeight; // truncates toward zero
            remainders[i] = Math.abs(numerator - shares[i] * totalWeight);
            distributed += shares[i];
        }

        // At most one minor unit per part is ever left over, and it carries
        // the sign of the original amount so negatives allocate correctly.
        long leftover = minorUnits - distributed;
        long step = leftover >= 0 ? 1 : -1;
        int[] byRemainderDescending = IntStream.range(0, weights.length)
                .boxed()
                .sorted(Comparator.<Integer, Long>comparing(i -> remainders[i])
                        .reversed()
                        .thenComparing(Comparator.naturalOrder()))
                .mapToInt(Integer::intValue)
                .toArray();

        for (int i = 0; i < Math.abs(leftover); i++) {
            shares[byRemainderDescending[i]] += step;
        }

        int digits = fractionDigits(currency);
        List<Money> parts = new ArrayList<>(weights.length);
        for (long share : shares) {
            parts.add(new Money(BigDecimal.valueOf(share, digits), currency));
        }
        return parts;
    }

    /** Splits evenly into {@code parts} pieces, remainder distributed. */
    public List<Money> allocateEvenly(int parts) {
        int[] weights = new int[parts];
        Arrays.fill(weights, 1);
        return allocate(weights);
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    public boolean isPositive() {
        return amount.signum() > 0;
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    /**
     * The amount as a whole number of the currency's smallest unit: cents for
     * USD, yen for JPY. This is the representation to put on a wire, where a
     * decimal string invites the other side to parse it into a float.
     */
    public long toMinorUnits() {
        return amount.movePointRight(fractionDigits(currency)).longValueExact();
    }

    public static Money ofMinorUnits(long minorUnits, String currency) {
        return new Money(BigDecimal.valueOf(minorUnits, fractionDigits(currency)), currency);
    }

    /**
     * @throws CurrencyMismatchException if the currencies differ, so an
     *         accidental comparison across currencies fails loudly instead of
     *         quietly ordering by a meaningless number.
     */
    @Override
    public int compareTo(Money other) {
        requireSameCurrency(other);
        return amount.compareTo(other.amount);
    }

    @Override
    public String toString() {
        return amount.toPlainString() + " " + currency;
    }

    private void requireSameCurrency(Money other) {
        if (!currency.equals(other.currency)) {
            throw new CurrencyMismatchException(currency, other.currency);
        }
    }

    private static int fractionDigits(String currency) {
        Currency known;
        try {
            known = Currency.getInstance(currency.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Not a known ISO 4217 currency code: " + currency);
        }
        // Gold and the like report -1; nothing in a ledger should be denominated
        // in those, and treating -1 as a scale would corrupt every amount.
        int digits = known.getDefaultFractionDigits();
        if (digits < 0) {
            throw new IllegalArgumentException(currency + " has no minor unit and cannot be used as a ledger currency");
        }
        return digits;
    }
}
