package com.ledgerflow.service;

import com.ledgerflow.domain.EntryType;
import com.ledgerflow.money.Money;

/**
 * One leg of a journal entry. The amount is always positive; which way the
 * value moves is carried by {@link EntryType}, not by a sign.
 */
public record EntryLine(Long accountId, EntryType entryType, Money amount) {}
