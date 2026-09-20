package com.ledgerflow.exception;

/**
 * A change to a contact, tax rate or item that its own validation forbids --
 * a blank name, a rate out of range, a SKU already in use.
 *
 * Carries a code for the same reason {@code ChartOfAccountsException} does:
 * so the API can say which rule was broken rather than an undifferentiated
 * 400 the client has to guess at.
 */
public class MasterDataException extends RuntimeException {

    private final String code;

    public MasterDataException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
