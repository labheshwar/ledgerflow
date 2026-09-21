package com.ledgerflow.exception;

/** An exchange rate that its own rules forbid recording, or a posting that needed one that was never recorded. */
public class FxRateException extends RuntimeException {

    private final String code;

    public FxRateException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
