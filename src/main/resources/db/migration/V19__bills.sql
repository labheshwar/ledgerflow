-- Bills: the mirror image of invoices. A header plus its lines, modeled the
-- same way in Java for the same reason (see V17's own comment).
--
-- A DRAFT is a plan, freely edited or deleted. Posting one draws this
-- application's own document number and posts a journal (DR each line's own
-- expense or asset account / DR Tax Receivable / CR Accounts Payable); once
-- posted, a bill is history and can only be voided by reversal, never
-- edited or deleted.
--
-- vendor_reference is a different thing from bill_number: it is the number
-- printed on the vendor's own bill, entered by whoever keys this one in,
-- and it is what the duplicate-vendor-bill guard below actually compares --
-- this application's own bill_number can never collide with anything, so it
-- could not catch the same vendor bill being entered twice.

CREATE TABLE bills (
    id                    BIGSERIAL     PRIMARY KEY,
    org_id                BIGINT        NOT NULL REFERENCES organizations(id),
    contact_id            BIGINT        NOT NULL REFERENCES contacts(id),
    -- Null until posted -- drawn from the same gapless counter invoices use,
    -- keyed by its own DocumentType so the two series never share numbers.
    bill_number           VARCHAR(20),
    vendor_reference      VARCHAR(50)   NOT NULL,
    status                VARCHAR(10)   NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'OPEN', 'VOID')),
    bill_date             DATE          NOT NULL,
    due_date              DATE          NOT NULL,
    currency              VARCHAR(3)    NOT NULL,
    notes                 VARCHAR(1000),
    -- The journal this bill became on posting. Null for a draft, and for a
    -- posted bill whose posting has not yet completed -- see BillSweeper.
    posted_transaction_id BIGINT        REFERENCES transactions(id),
    posted_at             TIMESTAMPTZ,
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CHECK (due_date >= bill_date)
);

CREATE UNIQUE INDEX idx_bills_org_number ON bills (org_id, bill_number) WHERE bill_number IS NOT NULL;
CREATE INDEX idx_bills_org_status ON bills (org_id, status);
CREATE INDEX idx_bills_contact ON bills (contact_id);

-- The duplicate-vendor-bill guard itself. VOID is excluded so a corrected
-- re-entry can reuse the same vendor reference once the original is
-- reversed -- the same reasoning invoices' own number index does not need,
-- because a voided invoice's number is never reissued to a different
-- document.
CREATE UNIQUE INDEX idx_bills_org_contact_vendor_reference
    ON bills (org_id, contact_id, vendor_reference) WHERE status <> 'VOID';

CREATE TABLE bill_lines (
    id          BIGSERIAL     PRIMARY KEY,
    org_id      BIGINT        NOT NULL REFERENCES organizations(id),
    bill_id     BIGINT        NOT NULL REFERENCES bills(id) ON DELETE CASCADE,
    line_order  INT           NOT NULL,
    -- Required, unlike invoice_lines.item_id: a bill line can be rent,
    -- office supplies or inventory in the same document, so there is no
    -- single default account the way invoicing always credits revenue.
    account_id  BIGINT        NOT NULL REFERENCES accounts(id),
    -- Optional: a line can still name an item to reuse its price and tax.
    item_id     BIGINT        REFERENCES items(id),
    description VARCHAR(500)  NOT NULL,
    quantity    NUMERIC(18,4) NOT NULL CHECK (quantity > 0),
    unit_price  NUMERIC(18,4) NOT NULL CHECK (unit_price >= 0),
    tax_rate_id BIGINT        REFERENCES tax_rates(id)
);

CREATE INDEX idx_bill_lines_bill ON bill_lines (bill_id, line_order);

ALTER TABLE bills ENABLE ROW LEVEL SECURITY;
ALTER TABLE bills FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON bills
    USING (org_id = NULLIF(current_setting('app.current_org', true), '')::bigint)
    WITH CHECK (org_id = NULLIF(current_setting('app.current_org', true), '')::bigint);

ALTER TABLE bill_lines ENABLE ROW LEVEL SECURITY;
ALTER TABLE bill_lines FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON bill_lines
    USING (org_id = NULLIF(current_setting('app.current_org', true), '')::bigint)
    WITH CHECK (org_id = NULLIF(current_setting('app.current_org', true), '')::bigint);

-- Same backfill V17 did for Tax Payable, now for Tax Receivable: every
-- organization created since milestone 6 gets one from
-- ChartOfAccountsSeeder's DefaultChartOfAccounts; the demo organization
-- predates that seed list and has never had one, and posting a taxed bill
-- has nowhere to put the reclaimable tax without it. RLS is disabled around
-- it for the same reason V17 gives: this INSERT spans every organization at
-- once and a migration has no tenant in context for the policy to compare
-- against.
ALTER TABLE accounts DISABLE ROW LEVEL SECURITY;

INSERT INTO accounts (org_id, code, name, type, currency, system_role, is_postable, created_at, updated_at)
SELECT
    o.id,
    CASE WHEN EXISTS (SELECT 1 FROM accounts a2 WHERE a2.org_id = o.id AND a2.code = '1200')
         THEN 'TAXR-' || o.id::text
         ELSE '1200' END,
    'Tax Receivable',
    'ASSET',
    o.base_currency,
    'TAX_RECEIVABLE',
    TRUE,
    now(),
    now()
FROM organizations o
WHERE NOT EXISTS (
    SELECT 1 FROM accounts a WHERE a.org_id = o.id AND a.system_role = 'TAX_RECEIVABLE'
);

ALTER TABLE accounts ENABLE ROW LEVEL SECURITY;
