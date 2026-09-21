package com.ledgerflow.exception;

/**
 * A change to a bill, or a transition of one, that its own rules forbid --
 * editing something already posted, entering the same vendor bill twice.
 *
 * Carries a code for the same reason {@code InvoiceException} does: so the
 * API can say which rule was broken rather than an undifferentiated 400.
 */
public class BillException extends RuntimeException {

    private final String code;

    public BillException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
