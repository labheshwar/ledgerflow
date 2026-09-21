-- Payments settle invoices and bills. One payment can settle several of
-- either, and can carry more than its allocations add up to -- the
-- remainder posts to a prepayment account rather than to any one document,
-- money received or paid before it was earmarked for anything specific.
--
-- There is no draft: recording a payment is recording something that
-- already happened, so it is created POSTED. VOID undoes it by reversal,
-- exactly like voiding an invoice or a bill.

CREATE TABLE payments (
    id                    BIGSERIAL     PRIMARY KEY,
    org_id                BIGINT        NOT NULL REFERENCES organizations(id),
    contact_id            BIGINT        NOT NULL REFERENCES contacts(id),
    direction             VARCHAR(10)   NOT NULL CHECK (direction IN ('RECEIVED', 'PAID')),
    status                VARCHAR(10)   NOT NULL DEFAULT 'POSTED' CHECK (status IN ('POSTED', 'VOID')),
    payment_date          DATE          NOT NULL,
    amount                NUMERIC(18,4) NOT NULL CHECK (amount > 0),
    currency              VARCHAR(3)    NOT NULL,
    notes                 VARCHAR(1000),
    -- Null only in the crash window between posting and this write-back --
    -- see PaymentSweeper.
    posted_transaction_id BIGINT        REFERENCES transactions(id),
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_payments_org_contact ON payments (org_id, contact_id);

-- entity_type/entity_id on attachments is the same shape: document_id
-- points at either invoices.id or bills.id depending on document_type, so
-- it cannot be a single foreign key.
CREATE TABLE payment_allocations (
    id            BIGSERIAL     PRIMARY KEY,
    org_id        BIGINT        NOT NULL REFERENCES organizations(id),
    payment_id    BIGINT        NOT NULL REFERENCES payments(id) ON DELETE CASCADE,
    document_type VARCHAR(10)   NOT NULL CHECK (document_type IN ('INVOICE', 'BILL')),
    document_id   BIGINT        NOT NULL,
    amount        NUMERIC(18,4) NOT NULL CHECK (amount > 0)
);

CREATE INDEX idx_payment_allocations_document ON payment_allocations (document_type, document_id);
CREATE INDEX idx_payment_allocations_payment ON payment_allocations (payment_id);

ALTER TABLE payments ENABLE ROW LEVEL SECURITY;
ALTER TABLE payments FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON payments
    USING (org_id = NULLIF(current_setting('app.current_org', true), '')::bigint)
    WITH CHECK (org_id = NULLIF(current_setting('app.current_org', true), '')::bigint);

ALTER TABLE payment_allocations ENABLE ROW LEVEL SECURITY;
ALTER TABLE payment_allocations FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON payment_allocations
    USING (org_id = NULLIF(current_setting('app.current_org', true), '')::bigint)
    WITH CHECK (org_id = NULLIF(current_setting('app.current_org', true), '')::bigint);

-- Same backfill V17 and V19 did for Tax Payable and Tax Receivable: every
-- organization created since milestone 6 gets both prepayment accounts
-- from ChartOfAccountsSeeder's DefaultChartOfAccounts, but the demo
-- organization predates that seed list and has never had either. RLS is
-- disabled around it for the same reason those migrations give: this
-- INSERT spans every organization at once and a migration has no tenant in
-- context for the policy to compare against.
ALTER TABLE accounts DISABLE ROW LEVEL SECURITY;

INSERT INTO accounts (org_id, code, name, type, currency, system_role, is_postable, created_at, updated_at)
SELECT
    o.id,
    CASE WHEN EXISTS (SELECT 1 FROM accounts a2 WHERE a2.org_id = o.id AND a2.code = '2200')
         THEN 'CPRE-' || o.id::text
         ELSE '2200' END,
    'Customer Prepayments',
    'LIABILITY',
    o.base_currency,
    'CUSTOMER_PREPAYMENTS',
    TRUE,
    now(),
    now()
FROM organizations o
WHERE NOT EXISTS (
    SELECT 1 FROM accounts a WHERE a.org_id = o.id AND a.system_role = 'CUSTOMER_PREPAYMENTS'
);

INSERT INTO accounts (org_id, code, name, type, currency, system_role, is_postable, created_at, updated_at)
SELECT
    o.id,
    CASE WHEN EXISTS (SELECT 1 FROM accounts a2 WHERE a2.org_id = o.id AND a2.code = '1300')
         THEN 'VPRE-' || o.id::text
         ELSE '1300' END,
    'Vendor Prepayments',
    'ASSET',
    o.base_currency,
    'VENDOR_PREPAYMENTS',
    TRUE,
    now(),
    now()
FROM organizations o
WHERE NOT EXISTS (
    SELECT 1 FROM accounts a WHERE a.org_id = o.id AND a.system_role = 'VENDOR_PREPAYMENTS'
);

ALTER TABLE accounts ENABLE ROW LEVEL SECURITY;
