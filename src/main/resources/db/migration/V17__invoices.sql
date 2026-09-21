-- Invoices: a header plus its lines, exactly like a transaction plus its
-- entries -- and deliberately modeled the same way in Java, as two flat
-- entities joined by a plain id rather than a JPA collection, for the same
-- reason Account.parentId is a plain id and not a @ManyToOne: an association
-- only invites a lazy proxy to be dereferenced somewhere the session has
-- already closed.
--
-- A DRAFT is a plan, freely edited or deleted. Sending one draws a document
-- number and posts a journal (DR Accounts Receivable / CR Sales Revenue /
-- CR Tax Payable); once sent, an invoice is history and can only be voided
-- by reversal, never edited or deleted -- the same rule accounts and
-- transactions already follow once anything has been posted.

CREATE TABLE invoices (
    id                    BIGSERIAL     PRIMARY KEY,
    org_id                BIGINT        NOT NULL REFERENCES organizations(id),
    contact_id            BIGINT        NOT NULL REFERENCES contacts(id),
    -- Null until sent -- drawn from the gapless counter in milestone 8's
    -- document_number_counters, in the same transaction as the status flip
    -- below, so a rolled-back send never burns a number.
    invoice_number        VARCHAR(20),
    status                VARCHAR(10)   NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'SENT', 'VOID')),
    issue_date            DATE          NOT NULL,
    due_date              DATE          NOT NULL,
    currency              VARCHAR(3)    NOT NULL,
    notes                 VARCHAR(1000),
    -- The journal this invoice became on send. Null for a draft, and for a
    -- sent invoice whose posting has not yet completed -- see InvoiceSweeper.
    posted_transaction_id BIGINT        REFERENCES transactions(id),
    sent_at               TIMESTAMPTZ,
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CHECK (due_date >= issue_date)
);

-- Unique per organization, and only once actually assigned -- every DRAFT
-- has a null number, and nulls must not collide with each other.
CREATE UNIQUE INDEX idx_invoices_org_number ON invoices (org_id, invoice_number) WHERE invoice_number IS NOT NULL;
CREATE INDEX idx_invoices_org_status ON invoices (org_id, status);
CREATE INDEX idx_invoices_contact ON invoices (contact_id);

CREATE TABLE invoice_lines (
    id          BIGSERIAL     PRIMARY KEY,
    org_id      BIGINT        NOT NULL REFERENCES organizations(id),
    invoice_id  BIGINT        NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
    line_order  INT           NOT NULL,
    -- Optional: a line can name an item to reuse its price and tax, or
    -- describe something one-off that was never worth cataloging.
    item_id     BIGINT        REFERENCES items(id),
    description VARCHAR(500)  NOT NULL,
    quantity    NUMERIC(18,4) NOT NULL CHECK (quantity > 0),
    unit_price  NUMERIC(18,4) NOT NULL CHECK (unit_price >= 0),
    tax_rate_id BIGINT        REFERENCES tax_rates(id)
);

CREATE INDEX idx_invoice_lines_invoice ON invoice_lines (invoice_id, line_order);

ALTER TABLE invoices ENABLE ROW LEVEL SECURITY;
ALTER TABLE invoices FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON invoices
    USING (org_id = NULLIF(current_setting('app.current_org', true), '')::bigint)
    WITH CHECK (org_id = NULLIF(current_setting('app.current_org', true), '')::bigint);

ALTER TABLE invoice_lines ENABLE ROW LEVEL SECURITY;
ALTER TABLE invoice_lines FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON invoice_lines
    USING (org_id = NULLIF(current_setting('app.current_org', true), '')::bigint)
    WITH CHECK (org_id = NULLIF(current_setting('app.current_org', true), '')::bigint);

-- Every organization created since milestone 6 gets a Tax Payable account
-- from ChartOfAccountsSeeder. Anything older -- in this codebase's history,
-- the demo organization -- does not, and sending a taxed invoice has nowhere
-- to post the tax collected without one. Same backfill shape V15 used for
-- Retained Earnings, and RLS is disabled around it for the same reason: this
-- INSERT spans every organization at once and a migration has no tenant in
-- context for the policy to compare against.
ALTER TABLE accounts DISABLE ROW LEVEL SECURITY;

INSERT INTO accounts (org_id, code, name, type, currency, system_role, is_postable, created_at, updated_at)
SELECT
    o.id,
    CASE WHEN EXISTS (SELECT 1 FROM accounts a2 WHERE a2.org_id = o.id AND a2.code = '2100')
         THEN 'TAX-' || o.id::text
         ELSE '2100' END,
    'Tax Payable',
    'LIABILITY',
    o.base_currency,
    'TAX_PAYABLE',
    TRUE,
    now(),
    now()
FROM organizations o
WHERE NOT EXISTS (
    SELECT 1 FROM accounts a WHERE a.org_id = o.id AND a.system_role = 'TAX_PAYABLE'
);

ALTER TABLE accounts ENABLE ROW LEVEL SECURITY;
