package com.ledgerflow.messaging;

/**
 * Carries the organization explicitly. A consumer thread has no HTTP request
 * and no security context to infer it from, and without it every query the
 * listener makes is filtered to nothing by row-level security.
 */
public record ReconciliationRequestedEvent(Long orgId, Long batchId) {}
