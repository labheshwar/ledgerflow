-- Milestone 14 replaces the org-wide, simulated-balance reconciliation
-- (one batch comparing every account's ledger balance against a fake
-- external feed) with a per-bank-account workspace that matches a real
-- imported statement's own lines against the ledger, one line at a time.
-- The old batches carry no information anything downstream depends on --
-- they were always compared against a feed that was never real -- so they
-- are dropped rather than migrated.
DROP TABLE IF EXISTS reconciliation_results;
DROP TABLE IF EXISTS reconciliation_batches;

-- A line accounts for itself once it points at the entry that now records
-- it -- an existing one it was matched to, or a new one a payment or a
-- manual categorization just posted. The partial unique index is the same
-- shape the statement import's own dedupe guard uses: it is what makes
-- "two different lines both claim the same entry" a constraint the
-- database enforces, not a race the application has to remember to check.
ALTER TABLE statement_lines ADD COLUMN matched_entry_id BIGINT REFERENCES entries(id);
ALTER TABLE statement_lines ADD COLUMN matched_at TIMESTAMPTZ;

CREATE UNIQUE INDEX idx_statement_lines_matched_entry ON statement_lines (matched_entry_id) WHERE matched_entry_id IS NOT NULL;

-- The workspace's own primary read: a bank account's unmatched (or
-- matched) lines, paged.
CREATE INDEX idx_statement_lines_bank_account_matched ON statement_lines (bank_account_id, committed, matched_entry_id);

-- A payment settling a line now posts its cash side to the specific bank
-- account the line belongs to, rather than always the organization's one
-- system CASH account -- see PaymentService. Null keeps every payment
-- recorded before this milestone, and every one still recorded through
-- the ordinary payment form, posting exactly where it always did.
ALTER TABLE payments ADD COLUMN bank_account_id BIGINT REFERENCES bank_accounts(id);
