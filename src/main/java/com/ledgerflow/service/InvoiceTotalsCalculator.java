package com.ledgerflow.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * The arithmetic behind an invoice's totals, kept as a pure function and
 * mirrored line for line in the frontend's {@code invoice-totals.ts} -- the
 * same split {@code journal-balance.ts} made from the posting form, so the
 * UI can show a live total without a round trip, while the number that
 * actually gets posted is computed here, from the same inputs, independently.
 *
 * Each line is rounded on its own before the totals are summed, matching how
 * a paper invoice is always totaled -- summing full-precision amounts and
 * rounding once at the end would show line prices that do not multiply out
 * to the line total sitting next to them.
 *
 * Two decimal places throughout: every organization here reports in a
 * currency with a two-digit minor unit today (see the "no currency
 * conversion yet" limitation), so there is nothing yet that needs this to
 * vary by currency.
 */
public final class InvoiceTotalsCalculator {

    private static final int SCALE = 2;
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private InvoiceTotalsCalculator() {}

    public static InvoiceTotals compute(List<InvoiceLineInput> lines) {
        List<InvoiceLineTotal> lineTotals = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal taxTotal = BigDecimal.ZERO;

        for (InvoiceLineInput line : lines) {
            BigDecimal lineSubtotal = line.quantity().multiply(line.unitPrice()).setScale(SCALE, RoundingMode.HALF_UP);
            BigDecimal taxRate = line.taxRatePercent() == null ? BigDecimal.ZERO : line.taxRatePercent();
            BigDecimal taxAmount = lineSubtotal.multiply(taxRate).divide(ONE_HUNDRED, SCALE, RoundingMode.HALF_UP);

            lineTotals.add(new InvoiceLineTotal(lineSubtotal, taxAmount, lineSubtotal.add(taxAmount)));
            subtotal = subtotal.add(lineSubtotal);
            taxTotal = taxTotal.add(taxAmount);
        }

        return new InvoiceTotals(lineTotals, subtotal, taxTotal, subtotal.add(taxTotal));
    }
}
