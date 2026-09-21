package com.ledgerflow.exception;

/**
 * A payment, or an allocation within one, that its own rules forbid --
 * allocating more than a payment's own amount, allocating more than a
 * document's remaining balance, settling a document that is not open to
 * begin with.
 *
 * Carries a code for the same reason {@code InvoiceException} does: so the
 * API can say which rule was broken rather than an undifferentiated 400.
 */
public class PaymentException extends RuntimeException {

    private final String code;

    public PaymentException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
