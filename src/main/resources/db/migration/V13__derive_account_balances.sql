-- Account balances stop being a stored number and become a fact derived
-- from the entries.
--
-- Why, concretely. accounts.balance was read-modify-written on every
-- posting, guarded by an optimistic-lock version. That is fine for a demo
-- where postings are rare and touch different accounts. It stops being fine
-- the moment the product exists: every invoice, payment, bill and expense
-- hits the same handful of rows -- Accounts Receivable, Accounts Payable,
-- Cash -- so two people invoicing at once in the same organization contend
-- on one row, and PostingService's three retries start being exhausted
-- under entirely ordinary use. The failure is a 500 under load and nothing
-- at all in testing.
--
-- A stored total has two further problems that no amount of locking fixes.
-- It can silently disagree with the entries that are supposed to explain it,
-- with no way to tell which is right. And it can only ever answer "what is
-- the balance now" -- not "what was it on 31 March", which is the question
-- every financial statement actually asks.
--
-- Derived has an obvious cost: summing an account's whole history on every
-- read. Snapshots bound that. A snapshot is a checkpoint -- the balance as
-- of a date -- so a read sums only the entries since. Correctness never
-- depends on one existing: with no snapshot the sum simply covers
-- everything, which is why the snapshot job can be late, fail, or be
-- deleted and rebuilt without any reader noticing.

CREATE TABLE account_balance_snapshots (
    id           BIGSERIAL     PRIMARY KEY,
    org_id       BIGINT        NOT NULL REFERENCES organizations(id),
    account_id   BIGINT        NOT NULL REFERENCES accounts(id),
    -- Includes every entry with txn_date <= as_of_date.
    as_of_date   DATE          NOT NULL,
    balance      NUMERIC(19,4) NOT NULL,
    base_balance NUMERIC(19,4) NOT NULL,
    -- How many entries went into it: lets a drift check compare a snapshot
    -- against the ledger without recomputing the money.
    entry_count  BIGINT        NOT NULL,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    -- Recomputing a period is an upsert, not a duplicate.
    UNIQUE (account_id, as_of_date)
);

-- The read path asks for the newest snapshot at or before a date, per
-- account. DESC matches that scan direction exactly.
CREATE INDEX idx_balance_snapshots_lookup
    ON account_balance_snapshots (account_id, as_of_date DESC);

ALTER TABLE account_balance_snapshots ENABLE ROW LEVEL SECURITY;
ALTER TABLE account_balance_snapshots FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON account_balance_snapshots
    USING (org_id = NULLIF(current_setting('app.current_org', true), '')::bigint)
    WITH CHECK (org_id = NULLIF(current_setting('app.current_org', true), '')::bigint);

-- Entries are now summed per account on every balance read, so the lookup
-- that used to be incidental is the hot path.
CREATE INDEX idx_entries_account_txn ON entries (account_id, transaction_id);

-- The column this replaces. Dropping it rather than leaving it to rot is the
-- point: two sources of truth for a balance is exactly the drift the derived
-- form exists to prevent, and a stale column is worse than no column because
-- it looks authoritative.
ALTER TABLE accounts DROP COLUMN balance;
