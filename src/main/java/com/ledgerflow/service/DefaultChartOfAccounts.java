package com.ledgerflow.service;

import static com.ledgerflow.domain.AccountType.ASSET;
import static com.ledgerflow.domain.AccountType.EQUITY;
import static com.ledgerflow.domain.AccountType.EXPENSE;
import static com.ledgerflow.domain.AccountType.LIABILITY;
import static com.ledgerflow.domain.AccountType.REVENUE;

import com.ledgerflow.domain.AccountType;
import com.ledgerflow.domain.SystemAccountRole;
import java.util.List;

/**
 * The chart a new organization starts with.
 *
 * A freshly created business with no accounts cannot do anything at all --
 * not even record that someone put money in. Worse, asking a non-accountant
 * to invent a chart from nothing is asking them to make decisions they have
 * no basis for, and to get the system accounts wrong in ways that only
 * surface later when invoicing cannot find its receivables account.
 *
 * So a small business chart is seeded: conventional numbering, the handful
 * of accounts a service business actually uses, and every system role
 * assigned. It is a starting point, not a straitjacket -- everything here can
 * be renamed, recoded, reparented or archived afterwards.
 */
final class DefaultChartOfAccounts {

    /**
     * @param role null for ordinary accounts; set only where the application
     *        itself needs to find the account later.
     */
    record Seed(
            String code,
            String name,
            AccountType type,
            SystemAccountRole role,
            boolean postable,
            String parentCode,
            String description) {

        static Seed of(String code, String name, AccountType type) {
            return new Seed(code, name, type, null, true, null, null);
        }

        static Seed of(String code, String name, AccountType type, SystemAccountRole role) {
            return new Seed(code, name, type, role, true, null, null);
        }

        Seed under(String parentCode) {
            return new Seed(code, name, type, role, postable, parentCode, description);
        }

        Seed heading() {
            return new Seed(code, name, type, role, false, parentCode, description);
        }

        Seed describedAs(String description) {
            return new Seed(code, name, type, role, postable, parentCode, description);
        }
    }

    /**
     * Ordered so that parents are always created before their children --
     * a heading has to exist before anything can be filed under it.
     *
     * The numbering is the usual convention: 1xxx assets, 2xxx liabilities,
     * 3xxx equity, 4xxx revenue, 5xxx-6xxx expenses. It is worth following
     * because a trial balance sorted by code then reads top to bottom as a
     * balance sheet followed by an income statement, which is the order every
     * accountant expects to find it in.
     */
    static final List<Seed> SEEDS = List.of(
            // --- Assets ---
            Seed.of("1000", "Bank Accounts", ASSET).heading(),
            Seed.of("1010", "Business Bank Account", ASSET, SystemAccountRole.CASH).under("1000"),
            Seed.of("1020", "Petty Cash", ASSET).under("1000"),
            Seed.of("1100", "Accounts Receivable", ASSET, SystemAccountRole.ACCOUNTS_RECEIVABLE)
                    .describedAs("What customers owe you"),
            Seed.of("1200", "Tax Receivable", ASSET, SystemAccountRole.TAX_RECEIVABLE)
                    .describedAs("Sales tax paid to suppliers and reclaimable"),
            Seed.of("1300", "Vendor Prepayments", ASSET, SystemAccountRole.VENDOR_PREPAYMENTS)
                    .describedAs("Money paid to a vendor before it was allocated to any of their bills"),

            // --- Liabilities ---
            Seed.of("2000", "Accounts Payable", LIABILITY, SystemAccountRole.ACCOUNTS_PAYABLE)
                    .describedAs("What you owe suppliers"),
            Seed.of("2100", "Tax Payable", LIABILITY, SystemAccountRole.TAX_PAYABLE)
                    .describedAs("Sales tax collected and owed onward"),
            Seed.of("2200", "Customer Prepayments", LIABILITY, SystemAccountRole.CUSTOMER_PREPAYMENTS)
                    .describedAs("Money received before anything was invoiced for it"),

            // --- Equity ---
            Seed.of("3000", "Owner Equity", EQUITY, SystemAccountRole.OWNER_EQUITY),
            Seed.of("3100", "Retained Earnings", EQUITY, SystemAccountRole.RETAINED_EARNINGS)
                    .describedAs("Accumulated profit from prior years"),

            // --- Revenue ---
            Seed.of("4000", "Sales", REVENUE, SystemAccountRole.SALES_REVENUE),
            Seed.of("4900", "Other Income", REVENUE),

            // --- Expenses ---
            Seed.of("5000", "Cost of Sales", EXPENSE),
            Seed.of("6000", "Operating Expenses", EXPENSE).heading(),
            Seed.of("6100", "Rent", EXPENSE).under("6000"),
            Seed.of("6200", "Software and Subscriptions", EXPENSE).under("6000"),
            Seed.of("6300", "Professional Fees", EXPENSE).under("6000"),
            Seed.of("6400", "Travel", EXPENSE).under("6000"),
            Seed.of("6900", "Bank Fees", EXPENSE).under("6000"),

            // Not under Operating Expenses: a currency movement is not
            // something the business chose to spend, and burying it among real
            // costs makes the expense line unreadable.
            Seed.of("7000", "Foreign Exchange Gain/Loss", EXPENSE, SystemAccountRole.FX_GAIN_LOSS)
                    .describedAs("Differences arising when foreign-currency balances settle"),
            Seed.of("7100", "Rounding", EXPENSE, SystemAccountRole.ROUNDING)
                    .describedAs("Sub-cent differences that allocation cannot avoid"));

    private DefaultChartOfAccounts() {}
}
