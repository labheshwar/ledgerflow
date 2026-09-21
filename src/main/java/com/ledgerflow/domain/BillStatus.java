package com.ledgerflow.domain;

/**
 * DRAFT is a plan, freely edited or deleted. OPEN is history: it has drawn a
 * document number and posted a journal, and can only be undone by voiding,
 * never edited. VOID is an open bill whose posting has been reversed.
 */
public enum BillStatus {
    DRAFT,
    OPEN,
    VOID
}
