package com.ledgerflow.exception;

/**
 * A change to an accounting period, or a posting against one, that the
 * period's own rules forbid.
 *
 * Carries a code for the same reason {@code ChartOfAccountsException} does:
 * so the API can say "that period is closed" rather than an undifferentiated
 * 400 the client has to guess at.
 */
public class PeriodException extends RuntimeException {

    private final String code;

    public PeriodException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
