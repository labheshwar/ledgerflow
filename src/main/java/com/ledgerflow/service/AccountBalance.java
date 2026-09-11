package com.ledgerflow.service;

import java.math.BigDecimal;

/**
 * The cached shape of a balance read -- deliberately not the JPA entity
 * itself, since caching a managed/proxied entity in Redis is asking for
 * serialization trouble.
 */
public record AccountBalance(Long accountId, BigDecimal balance, String currency) {
}
