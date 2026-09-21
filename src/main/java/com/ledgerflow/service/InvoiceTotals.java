package com.ledgerflow.service;

import java.math.BigDecimal;
import java.util.List;

public record InvoiceTotals(List<InvoiceLineTotal> lines, BigDecimal subtotal, BigDecimal taxTotal, BigDecimal grandTotal) {}
