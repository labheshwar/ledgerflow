package com.ledgerflow.exception;

/**
 * A change to the chart of accounts that the chart's own rules forbid --
 * a duplicate code, a cycle in the tree, archiving something still in use.
 *
 * Carries a code so the API can say which rule was broken rather than
 * returning an undifferentiated 400 and leaving the client to parse prose.
 */
public class ChartOfAccountsException extends RuntimeException {

    private final String code;

    public ChartOfAccountsException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
