-- Documents & delivery: a PDF an invoice can be rendered to, a place to keep
-- it (and anything else attached to a document), and a link a customer with
-- no LedgerFlow account of their own can still open.

CREATE TABLE attachments (
    id           BIGSERIAL     PRIMARY KEY,
    org_id       BIGINT        NOT NULL REFERENCES organizations(id),
    -- "INVOICE" today; a plain string rather than a foreign key because
    -- what it points at varies by row, the way entity_type/entity_id
    -- already works on audit_log.
    entity_type  VARCHAR(20)   NOT NULL,
    entity_id    BIGINT        NOT NULL,
    filename     VARCHAR(255)  NOT NULL,
    content_type VARCHAR(100)  NOT NULL,
    size_bytes   BIGINT        NOT NULL,
    -- The object's key in the S3-compatible store; the bytes themselves
    -- never touch Postgres.
    storage_key  VARCHAR(255)  NOT NULL UNIQUE,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_attachments_entity ON attachments (org_id, entity_type, entity_id);

ALTER TABLE attachments ENABLE ROW LEVEL SECURITY;
ALTER TABLE attachments FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON attachments
    USING (org_id = NULLIF(current_setting('app.current_org', true), '')::bigint)
    WITH CHECK (org_id = NULLIF(current_setting('app.current_org', true), '')::bigint);

-- Maps an unguessable token to the organization and invoice it means,
-- resolved by an anonymous request before any tenant exists to filter by.
-- Deliberately NOT row-level secured, and deliberately not carrying
-- anything but ids: the token itself -- 32 random bytes, generated
-- server-side when an invoice is sent -- is the access control, and once
-- resolved, every actual read of the invoice runs through the normal
-- tenant-scoped path (see PublicInvoiceController). Left out of
-- RlsPolicyVerifier's table list on purpose, for the same reason: this
-- table has no tenant to enforce, it hands one out.
CREATE TABLE invoice_public_links (
    token      VARCHAR(43) PRIMARY KEY,
    org_id     BIGINT      NOT NULL REFERENCES organizations(id),
    invoice_id BIGINT      NOT NULL REFERENCES invoices(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_invoice_public_links_invoice ON invoice_public_links (invoice_id);
