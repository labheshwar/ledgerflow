package com.ledgerflow.service;

import com.ledgerflow.domain.EntryType;
import java.math.BigDecimal;

public record EntryLine(Long accountId, EntryType entryType, BigDecimal amount) {
}
