package com.ledgerflow.repository;

/**
 * The one SQL expression for a derived balance, written once.
 *
 * Every read path -- a single account, a page of them, the dashboard total,
 * reconciliation -- needs the same arithmetic, and three hand-written copies
 * of it would be three chances to disagree about what an account is worth.
 *
 * The shape is: take the newest snapshot at or before the cutoff, then add
 * every entry since. Both halves are LEFT JOIN LATERAL, which is what makes
 * the snapshot optional -- with none, s.* is NULL, the entry filter degrades
 * to "all of history", and the answer is still right. That is the property
 * worth protecting: the snapshot job is an optimization that can be late,
 * fail, or be wiped and rebuilt, and no reader can tell.
 *
 * The second lateral references the first, which is legal because s is
 * joined before d.
 */
final class BalanceQueries {

    /**
     * Joins derived balances onto `accounts a`. The caller supplies the FROM
     * clause and any filtering, so this fragment stays the only place the
     * arithmetic lives.
     */
    static final String DERIVED_BALANCE_JOINS =
            """
            LEFT JOIN LATERAL (
                SELECT sn.as_of_date, sn.balance, sn.base_balance
                FROM account_balance_snapshots sn
                WHERE sn.account_id = a.id
                ORDER BY sn.as_of_date DESC
                LIMIT 1
            ) s ON TRUE
            LEFT JOIN LATERAL (
                SELECT
                    SUM(CASE WHEN e.entry_type = 'DEBIT' THEN  e.amount
                             ELSE -e.amount END) AS delta,
                    SUM(CASE WHEN e.entry_type = 'DEBIT' THEN  e.base_amount
                             ELSE -e.base_amount END) AS base_delta
                FROM entries e
                JOIN transactions t ON t.id = e.transaction_id
                WHERE e.account_id = a.id
                  AND (s.as_of_date IS NULL OR t.txn_date > s.as_of_date)
            ) d ON TRUE
            """;

    /** Balance in the account's own currency. */
    static final String BALANCE = "COALESCE(s.balance, 0) + COALESCE(d.delta, 0)";

    /** The same balance in the organization's reporting currency. */
    static final String BASE_BALANCE = "COALESCE(s.base_balance, 0) + COALESCE(d.base_delta, 0)";

    private BalanceQueries() {}
}
