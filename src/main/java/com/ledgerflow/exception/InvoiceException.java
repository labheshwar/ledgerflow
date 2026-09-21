package com.ledgerflow.exception;

/**
 * A change to an invoice, or a transition of one, that its own rules forbid --
 * editing something already sent, sending one with nothing to charge.
 *
 * Carries a code for the same reason {@code PeriodException} does: so the API
 * can say which rule was broken rather than an undifferentiated 400.
 */
public class InvoiceException extends RuntimeException {

    private final String code;

    public InvoiceException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
