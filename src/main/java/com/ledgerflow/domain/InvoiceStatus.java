package com.ledgerflow.domain;

/**
 * DRAFT is a plan, freely edited or deleted. SENT is history: it has drawn a
 * document number and posted a journal, and can only be undone by voiding,
 * never edited. VOID is a sent invoice whose posting has been reversed.
 */
public enum InvoiceStatus {
    DRAFT,
    SENT,
    VOID
}
