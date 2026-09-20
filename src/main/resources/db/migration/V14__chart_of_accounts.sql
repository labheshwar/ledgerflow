-- Turns a flat list of accounts into a chart of accounts.
--
-- Four things a real chart has that a list does not:
--
--   code         What accountants actually refer to an account by, and what
--                every import, export and trial balance is ordered by. Names
--                get edited; codes are the stable handle.
--   parent_id    Headers with children beneath them. "Expenses" is not an
--                account you post to, it is a heading that sums the ones you do.
--   is_postable  Whether entries may land here. Headers cannot receive them,
--                or the total would double-count its own children.
--   system_role  Which account the *application* means when it says "accounts
--                receivable". Invoicing has to find it without asking the user
--                and without matching on a name someone is free to rename.
--
-- Plus archived_at, because an account that has ever been posted to can never
-- be deleted -- the entries reference it and the history has to stay
-- explicable. Archiving hides it from pickers and leaves the ledger intact.

ALTER TABLE accounts ADD COLUMN code        VARCHAR(20);
ALTER TABLE accounts ADD COLUMN parent_id   BIGINT REFERENCES accounts(id);
ALTER TABLE accounts ADD COLUMN system_role VARCHAR(40);
ALTER TABLE accounts ADD COLUMN is_postable BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE accounts ADD COLUMN archived_at TIMESTAMPTZ;
ALTER TABLE accounts ADD COLUMN description VARCHAR(500);

-- Backfill with conventional numbering: 1xxx assets, 2xxx liabilities,
-- 3xxx equity, 4xxx revenue, 5xxx expenses. This ordering is why a trial
-- balance sorted by code reads top-down as a balance sheet followed by an
-- income statement.
--
-- RLS is disabled around the backfill for the reason given in V12: FORCE ROW
-- LEVEL SECURITY applies the tenant policy to the table owner too, and a
-- migration has no tenant in context, so these UPDATEs would match nothing
-- and silently succeed.
ALTER TABLE accounts DISABLE ROW LEVEL SECURITY;

UPDATE accounts SET code = '1000', system_role = 'CASH'                WHERE name = 'Cash';
UPDATE accounts SET code = '1100', system_role = 'ACCOUNTS_RECEIVABLE' WHERE name = 'Accounts Receivable';
UPDATE accounts SET code = '2000', system_role = 'ACCOUNTS_PAYABLE'    WHERE name = 'Accounts Payable';
UPDATE accounts SET code = '3000', system_role = 'OWNER_EQUITY'        WHERE name = 'Owner Equity';
UPDATE accounts SET code = '4000', system_role = 'SALES_REVENUE'       WHERE name = 'Revenue';
UPDATE accounts SET code = '5000'                                      WHERE name = 'Operating Expenses';

-- Anything else that predates this migration gets a code derived from its id,
-- so the NOT NULL below cannot fail on data this project has never seen.
UPDATE accounts SET code = '9' || LPAD(id::text, 4, '0') WHERE code IS NULL;

ALTER TABLE accounts ENABLE ROW LEVEL SECURITY;

ALTER TABLE accounts ALTER COLUMN code SET NOT NULL;

-- Unique per organization, not globally: two businesses both numbering their
-- cash account 1000 is the normal case, not a collision.
ALTER TABLE accounts ADD CONSTRAINT accounts_org_code_key UNIQUE (org_id, code);

-- At most one account per role per organization. A partial index rather than
-- a constraint, because most accounts have no system role and NULLs must not
-- collide with each other.
CREATE UNIQUE INDEX accounts_org_system_role_key
    ON accounts (org_id, system_role)
    WHERE system_role IS NOT NULL;

CREATE INDEX idx_accounts_parent ON accounts (parent_id);

-- An account cannot be its own parent. Deeper cycles are prevented in the
-- service, which walks the chain -- SQL cannot express that without a trigger,
-- and a trigger here would fire on every write to catch a case the UI makes
-- nearly impossible to reach.
ALTER TABLE accounts ADD CONSTRAINT accounts_not_own_parent CHECK (parent_id IS NULL OR parent_id <> id);

ALTER TABLE accounts ADD CONSTRAINT accounts_code_format CHECK (code ~ '^[A-Za-z0-9][A-Za-z0-9.\-]*$');
