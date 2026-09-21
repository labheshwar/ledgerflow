package com.ledgerflow.exception;

/** A reconciliation action the workspace's own rules forbid -- same shape as {@code PaymentException} and its siblings. */
public class ReconciliationException extends RuntimeException {

    private final String code;

    public ReconciliationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
