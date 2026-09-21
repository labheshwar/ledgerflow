package com.ledgerflow.exception;

/**
 * A statement import, or a step of its wizard taken out of order, that its
 * own rules forbid -- previewing before a mapping is given, committing
 * before a preview exists.
 *
 * Carries a code for the same reason {@code InvoiceException} does: so the
 * API can say which rule was broken rather than an undifferentiated 400.
 */
public class StatementImportException extends RuntimeException {

    private final String code;

    public StatementImportException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
