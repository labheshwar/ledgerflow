-- Period locking and reversal.
--
-- Locking: an accounting period, once closed, refuses new postings dated
-- inside it. Reversal: the mechanical way to undo a posting without ever
-- mutating or deleting one -- history stays exactly what it always was, and
-- the correction is a second, ordinary entry that happens to point back at
-- the first.

-- Equality comparisons (org_id) inside a GIST index need this; it is a
-- "trusted" extension in modern Postgres, installable without superuser,
-- though the migration role here is one anyway.
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE accounting_periods (
    id         BIGSERIAL     PRIMARY KEY,
    org_id     BIGINT        NOT NULL REFERENCES organizations(id),
    start_date DATE          NOT NULL,
    end_date   DATE          NOT NULL,
    status     VARCHAR(10)   NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'CLOSED')),
    closed_at  TIMESTAMPTZ,
    created_at TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CHECK (end_date >= start_date),

    -- No two periods for one organization may cover the same date, whether
    -- open or closed. Checked here rather than in the service because the
    -- alternative -- ask "does anything overlap?" then insert -- is a race:
    -- two requests creating adjacent-looking periods at once could both pass
    -- the check and both insert. The database is the only thing that sees
    -- every concurrent attempt at once.
    EXCLUDE USING gist (org_id WITH =, daterange(start_date, end_date, '[]') WITH &&)
);

ALTER TABLE accounting_periods ENABLE ROW LEVEL SECURITY;
ALTER TABLE accounting_periods FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON accounting_periods
    USING (org_id = NULLIF(current_setting('app.current_org', true), '')::bigint)
    WITH CHECK (org_id = NULLIF(current_setting('app.current_org', true), '')::bigint);

-- A reversal is an ordinary transaction that happens to carry a pointer back
-- to the one it undoes. Nothing about posting changes; the entries are the
-- mirror image (same accounts and amounts, opposite DEBIT/CREDIT) of the
-- original's, built and validated by the same JournalBuilder as any other
-- journal. "Has this been reversed" is answered by looking for a row whose
-- reversal_of_transaction_id points at it -- not by a status flag on the
-- original -- so there is exactly one place this fact can disagree with
-- itself, and it is not writable after the fact.
ALTER TABLE transactions ADD COLUMN reversal_of_transaction_id BIGINT REFERENCES transactions(id);

-- At most one direct reversal per transaction. A second correction to an
-- already-reversed entry reverses the reversal, rather than creating two
-- competing corrections of the original with no ordering between them.
CREATE UNIQUE INDEX idx_transactions_reversal_of_unique
    ON transactions (reversal_of_transaction_id)
    WHERE reversal_of_transaction_id IS NOT NULL;

-- The database-level backstop. The application checks this too, before ever
-- reaching an INSERT, so a caller gets a clear 400 rather than a raw
-- constraint violation -- but the app-level check is a courtesy, not the
-- defense. This trigger is what actually makes "closed means closed" true
-- regardless of which code path, present or future, tries to write here.
CREATE OR REPLACE FUNCTION reject_posting_into_closed_period() RETURNS TRIGGER AS $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM accounting_periods p
        WHERE p.org_id = NEW.org_id
          AND p.status = 'CLOSED'
          AND NEW.txn_date BETWEEN p.start_date AND p.end_date
    ) THEN
        RAISE EXCEPTION 'org % has a closed accounting period covering %', NEW.org_id, NEW.txn_date;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_transactions_reject_closed_period
    BEFORE INSERT ON transactions
    FOR EACH ROW
    EXECUTE FUNCTION reject_posting_into_closed_period();

-- Every organization created from here on gets a Retained Earnings account
-- from ChartOfAccountsSeeder (milestone 6). Anything that predates that --
-- in this codebase's history, the demo organization -- does not, and the
-- year-end close this milestone adds has nowhere to post net income without
-- one. RLS is disabled for the same reason V12 and V14 disable it here: this
-- INSERT spans every organization at once and there is no tenant in context
-- during a migration for the policy to compare against.
ALTER TABLE accounts DISABLE ROW LEVEL SECURITY;

INSERT INTO accounts (org_id, code, name, type, currency, system_role, is_postable, created_at, updated_at)
SELECT
    o.id,
    CASE WHEN EXISTS (SELECT 1 FROM accounts a2 WHERE a2.org_id = o.id AND a2.code = '3100')
         THEN 'RE-' || o.id::text
         ELSE '3100' END,
    'Retained Earnings',
    'EQUITY',
    o.base_currency,
    'RETAINED_EARNINGS',
    TRUE,
    now(),
    now()
FROM organizations o
WHERE NOT EXISTS (
    SELECT 1 FROM accounts a WHERE a.org_id = o.id AND a.system_role = 'RETAINED_EARNINGS'
);

ALTER TABLE accounts ENABLE ROW LEVEL SECURITY;
