package com.ledgerflow.exception;

public class UnbalancedTransactionException extends RuntimeException {

    public UnbalancedTransactionException(String message) {
        super(message);
    }
}
