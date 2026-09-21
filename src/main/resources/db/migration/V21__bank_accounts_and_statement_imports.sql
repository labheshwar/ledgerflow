-- Bank accounts and the CSV wizard that imports a statement for one: file,
-- then a column mapping, then a worker-computed preview, then a commit.
--
-- A bank account is a thin wrapper around one GL account (account_id) --
-- the descriptive metadata a chart-of-accounts row has no reason to carry,
-- not a replacement for the account itself. Nothing here posts to the
-- ledger; that is milestone 14's reconciliation workspace, matching a
-- committed line here against an actual entry.

CREATE TABLE bank_accounts (
    id                   BIGSERIAL    PRIMARY KEY,
    org_id               BIGINT       NOT NULL REFERENCES organizations(id),
    account_id           BIGINT       NOT NULL REFERENCES accounts(id),
    name                 VARCHAR(255) NOT NULL,
    account_number_last4 VARCHAR(4),
    currency             VARCHAR(3)   NOT NULL,
    archived_at          TIMESTAMPTZ,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_bank_accounts_org ON bank_accounts (org_id);

CREATE TABLE statement_imports (
    id                   BIGSERIAL     PRIMARY KEY,
    org_id               BIGINT        NOT NULL REFERENCES organizations(id),
    bank_account_id      BIGINT        NOT NULL REFERENCES bank_accounts(id),
    status               VARCHAR(12)   NOT NULL DEFAULT 'UPLOADED'
                             CHECK (status IN ('UPLOADED', 'PROCESSING', 'PREVIEWED', 'COMMITTED', 'FAILED')),
    original_filename    VARCHAR(255)  NOT NULL,
    -- The raw file as uploaded, in the same S3-compatible store an
    -- invoice's own rendered PDF and attachments already live in.
    storage_key          VARCHAR(255)  NOT NULL,
    -- Null until the mapping step; all four name one of the CSV's own
    -- header names, not a fixed column position, since where a bank puts
    -- "Amount" varies file to file even from the same bank.
    date_column          VARCHAR(255),
    description_column   VARCHAR(255),
    amount_column        VARCHAR(255),
    -- Optional: falls back to a computed hash of date/description/amount
    -- when the bank's own export has no per-transaction reference.
    external_id_column   VARCHAR(255),
    total_rows           INT           NOT NULL DEFAULT 0,
    processed_rows       INT           NOT NULL DEFAULT 0,
    new_rows             INT           NOT NULL DEFAULT 0,
    duplicate_rows       INT           NOT NULL DEFAULT 0,
    error_rows           INT           NOT NULL DEFAULT 0,
    error_message        VARCHAR(1000),
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    completed_at         TIMESTAMPTZ
);

CREATE INDEX idx_statement_imports_bank_account ON statement_imports (bank_account_id, created_at DESC);

CREATE TABLE statement_lines (
    id              BIGSERIAL     PRIMARY KEY,
    org_id          BIGINT        NOT NULL REFERENCES organizations(id),
    bank_account_id BIGINT        NOT NULL REFERENCES bank_accounts(id),
    import_id       BIGINT        NOT NULL REFERENCES statement_imports(id) ON DELETE CASCADE,
    external_id     VARCHAR(255)  NOT NULL,
    txn_date        DATE          NOT NULL,
    description     VARCHAR(500)  NOT NULL,
    -- Signed the way the statement itself reads -- positive in, negative
    -- out -- not a debit/credit pair, since nothing has matched this to
    -- the ledger yet.
    amount          NUMERIC(18,4) NOT NULL,
    committed       BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_statement_lines_import ON statement_lines (import_id, committed);
CREATE INDEX idx_statement_lines_bank_account ON statement_lines (bank_account_id, committed, txn_date DESC);

-- The dedupe guard itself, and the reason committed rows and staged preview
-- rows can share the same external_id without conflict: an abandoned or
-- re-previewed import's own staged rows must never block a later import
-- from claiming the same one, the same reasoning the duplicate-vendor-bill
-- guard's own partial index already applies to voided bills.
CREATE UNIQUE INDEX idx_statement_lines_dedupe
    ON statement_lines (bank_account_id, external_id) WHERE committed;

ALTER TABLE bank_accounts ENABLE ROW LEVEL SECURITY;
ALTER TABLE bank_accounts FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON bank_accounts
    USING (org_id = NULLIF(current_setting('app.current_org', true), '')::bigint)
    WITH CHECK (org_id = NULLIF(current_setting('app.current_org', true), '')::bigint);

ALTER TABLE statement_imports ENABLE ROW LEVEL SECURITY;
ALTER TABLE statement_imports FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON statement_imports
    USING (org_id = NULLIF(current_setting('app.current_org', true), '')::bigint)
    WITH CHECK (org_id = NULLIF(current_setting('app.current_org', true), '')::bigint);

ALTER TABLE statement_lines ENABLE ROW LEVEL SECURITY;
ALTER TABLE statement_lines FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON statement_lines
    USING (org_id = NULLIF(current_setting('app.current_org', true), '')::bigint)
    WITH CHECK (org_id = NULLIF(current_setting('app.current_org', true), '')::bigint);
