package com.ledgerflow.messaging;

/**
 * Carries the organization explicitly, the same reason
 * {@link ReconciliationRequestedEvent} does: a consumer thread has no
 * request and no tenant to infer it from.
 */
public record InvoiceEmailRequestedEvent(Long orgId, Long invoiceId, String recipientEmail, boolean reminder) {}
