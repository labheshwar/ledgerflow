package com.ledgerflow.domain;

/**
 * A payment records something that already happened in the real world, so
 * there is no DRAFT the way an invoice or bill has one to edit before
 * committing -- POSTED is the only state a payment is created into. VOID
 * undoes it by reversal, exactly like voiding an invoice or a bill.
 */
public enum PaymentStatus {
    POSTED,
    VOID
}
