package com.ledgerflow.service;

import java.math.BigDecimal;

/**
 * What {@link InvoiceTotalsCalculator} needs from one line -- already
 * resolved to a percentage, not a tax rate id, so the calculator itself
 * stays a pure function with nothing to look up.
 */
public record InvoiceLineInput(BigDecimal quantity, BigDecimal unitPrice, BigDecimal taxRatePercent) {}
