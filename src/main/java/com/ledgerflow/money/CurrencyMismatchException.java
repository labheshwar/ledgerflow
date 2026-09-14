package com.ledgerflow.money;

/**
 * Thrown when two amounts in different currencies are combined.
 *
 * This is a programming error, not a user error -- nothing in the domain
 * should ever try to add dollars to euros, and if it does, the answer is
 * meaningless rather than merely wrong. It is unchecked because there is no
 * sensible recovery: the caller cannot pick a currency on the user's behalf.
 */
public class CurrencyMismatchException extends RuntimeException {

    public CurrencyMismatchException(String left, String right) {
        super("Cannot combine amounts in %s and %s".formatted(left, right));
    }
}
