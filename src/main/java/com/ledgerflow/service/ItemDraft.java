package com.ledgerflow.service;

import java.math.BigDecimal;

/** The editable shape of an item, used for both create and update. */
public record ItemDraft(
        String sku, String name, String description, BigDecimal defaultUnitPrice, Long defaultTaxRateId) {}
