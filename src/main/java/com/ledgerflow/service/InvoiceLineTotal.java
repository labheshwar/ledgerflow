package com.ledgerflow.service;

import java.math.BigDecimal;

public record InvoiceLineTotal(BigDecimal lineSubtotal, BigDecimal taxAmount, BigDecimal lineTotal) {}
