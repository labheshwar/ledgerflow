package com.ledgerflow.service;

import java.math.BigDecimal;

/** The editable shape of a tax rate, used for both create and update. */
public record TaxRateDraft(String name, BigDecimal rate) {}
