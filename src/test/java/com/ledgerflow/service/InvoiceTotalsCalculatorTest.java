package com.ledgerflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class InvoiceTotalsCalculatorTest {

    @Test
    void oneLineWithNoTaxTotalsToItsOwnSubtotal() {
        InvoiceTotals totals = InvoiceTotalsCalculator.compute(
                List.of(new InvoiceLineInput(new BigDecimal("2"), new BigDecimal("50.00"), null)));

        assertThat(totals.subtotal()).isEqualByComparingTo("100.00");
        assertThat(totals.taxTotal()).isEqualByComparingTo("0.00");
        assertThat(totals.grandTotal()).isEqualByComparingTo("100.00");
    }

    @Test
    void taxIsComputedPerLineFromThatLinesOwnRate() {
        InvoiceTotals totals = InvoiceTotalsCalculator.compute(List.of(
                new InvoiceLineInput(new BigDecimal("1"), new BigDecimal("100.00"), new BigDecimal("15")),
                new InvoiceLineInput(new BigDecimal("1"), new BigDecimal("50.00"), new BigDecimal("5"))));

        // Line 1: 100 + 15% = 15.00 tax. Line 2: 50 + 5% = 2.50 tax.
        assertThat(totals.subtotal()).isEqualByComparingTo("150.00");
        assertThat(totals.taxTotal()).isEqualByComparingTo("17.50");
        assertThat(totals.grandTotal()).isEqualByComparingTo("167.50");
        assertThat(totals.lines()).hasSize(2);
        assertThat(totals.lines().get(0).taxAmount()).isEqualByComparingTo("15.00");
        assertThat(totals.lines().get(1).taxAmount()).isEqualByComparingTo("2.50");
    }

    @Test
    void eachLineIsRoundedBeforeSummingRatherThanRoundedOnceAtTheEnd() {
        // 3 units at 33.335 rounds per-line to 100.01 (3 x 33.335 = 100.005,
        // half-up to 100.01) -- summing full precision first and rounding
        // once at the end would give a different total that no single line
        // on the invoice actually shows.
        InvoiceTotals totals = InvoiceTotalsCalculator.compute(
                List.of(new InvoiceLineInput(new BigDecimal("3"), new BigDecimal("33.335"), null)));

        assertThat(totals.lines().get(0).lineSubtotal()).isEqualByComparingTo("100.01");
        assertThat(totals.subtotal()).isEqualByComparingTo("100.01");
    }

    @Test
    void aLineWithNoTaxRateIsTreatedAsUntaxed() {
        InvoiceTotals totals = InvoiceTotalsCalculator.compute(
                List.of(new InvoiceLineInput(BigDecimal.ONE, new BigDecimal("20.00"), null)));

        assertThat(totals.taxTotal()).isEqualByComparingTo("0.00");
        assertThat(totals.lines().get(0).taxAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void noLinesTotalsToZero() {
        InvoiceTotals totals = InvoiceTotalsCalculator.compute(List.of());

        assertThat(totals.subtotal()).isEqualByComparingTo("0");
        assertThat(totals.taxTotal()).isEqualByComparingTo("0");
        assertThat(totals.grandTotal()).isEqualByComparingTo("0");
        assertThat(totals.lines()).isEmpty();
    }
}
