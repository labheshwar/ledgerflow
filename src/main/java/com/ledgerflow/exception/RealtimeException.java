package com.ledgerflow.exception;

/** An SSE ticket that is missing, expired, or already used. */
public class RealtimeException extends RuntimeException {

    private final String code;

    public RealtimeException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
