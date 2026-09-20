package com.ledgerflow.domain;

/**
 * Accounts the application itself has to be able to find.
 *
 * When invoicing posts "debit receivables, credit revenue", it needs a
 * specific account id and cannot ask the user mid-transaction. Matching on
 * the name is not an option -- names are the user's to change, and "Accounts
 * Receivable" becomes "Debtors" the moment someone from outside the US
 * configures the system. A role is a stable handle that survives renaming,
 * recoding and reordering.
 *
 * At most one account per role per organization, enforced by a partial unique
 * index in V14. Most accounts have no role at all.
 */
public enum SystemAccountRole {

    /** Where money actually sits. Bank and cash on hand. */
    CASH,

    /** What customers owe. Invoicing debits this when an invoice is issued. */
    ACCOUNTS_RECEIVABLE,

    /** What is owed to suppliers. Bills credit this. */
    ACCOUNTS_PAYABLE,

    /** The owner's stake. */
    OWNER_EQUITY,

    /**
     * Where the year's profit is closed to. The year-end closing journal
     * moves every revenue and expense balance here so the next year starts
     * from zero.
     */
    RETAINED_EARNINGS,

    /** Default income account. */
    SALES_REVENUE,

    /** Sales tax collected on behalf of the tax authority, owed onward. */
    TAX_PAYABLE,

    /** Tax paid to suppliers and reclaimable. */
    TAX_RECEIVABLE,

    /**
     * Where the difference goes when a foreign-currency invoice settles at a
     * rate other than the one it was raised at. Nothing posts here until
     * exchange rates exist.
     */
    FX_GAIN_LOSS,

    /**
     * Absorbs sub-cent differences that allocation cannot avoid, so a
     * journal still balances to the cent.
     */
    ROUNDING,

    /** Money received before anything was invoiced for it. */
    CUSTOMER_PREPAYMENTS
}
