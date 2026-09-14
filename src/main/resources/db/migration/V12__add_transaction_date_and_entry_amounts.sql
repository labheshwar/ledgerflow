-- Two invariants the schema was missing.
--
-- Note the DISABLE/ENABLE around each backfill. From V10 onwards these
-- tables carry FORCE ROW LEVEL SECURITY, which applies the tenant policy to
-- the table owner as well -- so an UPDATE run by a migration, which has no
-- tenant in context, would match zero rows and silently do nothing. It
-- happens to work today only because the migration role is a superuser, and
-- a migration that depends on that is a migration that breaks the first time
-- someone tightens the deployment. Flyway runs each script in a transaction
-- and PostgreSQL has transactional DDL, so a failure mid-script rolls the
-- disable back with everything else. FORCE is a separate flag that ENABLE
-- does not touch, and RlsPolicyVerifier refuses to boot if either is off.

-- 1. An accounting date, distinct from when the row was inserted.
--
-- These are genuinely different facts. A bookkeeper entering Friday's
-- invoice on Monday needs it to land in Friday's books; a correction filed
-- in April against March has to report in March. Every financial statement,
-- every period close and every exchange rate lookup keys off the date the
-- transaction is *effective*, not the moment a row happened to be written.
-- Ordering a ledger by created_at -- which is what this codebase did --
-- produces a running balance that disagrees with the accounts.
ALTER TABLE transactions ADD COLUMN txn_date DATE;

ALTER TABLE transactions DISABLE ROW LEVEL SECURITY;
UPDATE transactions SET txn_date = (created_at AT TIME ZONE 'UTC')::date;
ALTER TABLE transactions ENABLE ROW LEVEL SECURITY;

ALTER TABLE transactions ALTER COLUMN txn_date SET NOT NULL;

-- Reports and period queries all filter by organization and date range.
CREATE INDEX idx_transactions_org_txn_date ON transactions (org_id, txn_date);

-- 2. Every entry carries two amounts.
--
-- `amount` + `currency` is what the transaction was actually denominated in;
-- it drives customer statements and the receivable/payable subledger, where
-- a customer invoiced in euros expects to see euros. `base_amount` is the
-- same value in the organization's reporting currency, frozen at the rate
-- that applied when it was posted.
--
-- Storing both is what keeps reports from joining a rates table at query
-- time -- which would be slow, and worse, would restate last year's profit
-- every time today's rate moved.
ALTER TABLE entries ADD COLUMN currency    VARCHAR(3);
ALTER TABLE entries ADD COLUMN base_amount NUMERIC(19,4);
ALTER TABLE entries ADD COLUMN fx_rate     NUMERIC(19,8);

ALTER TABLE entries DISABLE ROW LEVEL SECURITY;
UPDATE entries e
SET currency    = a.currency,
    base_amount = e.amount,
    fx_rate     = 1
FROM accounts a
WHERE a.id = e.account_id;
ALTER TABLE entries ENABLE ROW LEVEL SECURITY;

ALTER TABLE entries ALTER COLUMN currency    SET NOT NULL;
ALTER TABLE entries ALTER COLUMN base_amount SET NOT NULL;
ALTER TABLE entries ALTER COLUMN fx_rate     SET NOT NULL;

ALTER TABLE entries ADD CONSTRAINT entries_base_amount_positive CHECK (base_amount > 0);
ALTER TABLE entries ADD CONSTRAINT entries_fx_rate_positive CHECK (fx_rate > 0);
