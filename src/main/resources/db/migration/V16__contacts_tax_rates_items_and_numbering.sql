-- Reference data an invoice or bill will need to point at: who it is for,
-- what tax applies, and what is being sold or bought. None of this posts
-- anything by itself -- it is the vocabulary the next few milestones write
-- invoices and bills in.

CREATE TABLE contacts (
    id            BIGSERIAL     PRIMARY KEY,
    org_id        BIGINT        NOT NULL REFERENCES organizations(id),
    type          VARCHAR(10)   NOT NULL CHECK (type IN ('CUSTOMER', 'VENDOR', 'BOTH')),
    name          VARCHAR(255)  NOT NULL,
    email         VARCHAR(255),
    phone         VARCHAR(50),
    tax_id        VARCHAR(50),
    address_line1 VARCHAR(255),
    address_line2 VARCHAR(255),
    city          VARCHAR(100),
    state         VARCHAR(100),
    postal_code   VARCHAR(20),
    country       VARCHAR(100),
    notes         VARCHAR(1000),
    archived_at   TIMESTAMPTZ,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE TABLE tax_rates (
    id          BIGSERIAL     PRIMARY KEY,
    org_id      BIGINT        NOT NULL REFERENCES organizations(id),
    name        VARCHAR(255)  NOT NULL,
    -- A percentage, not a fraction -- "15" means 15%, matching how a rate is
    -- always spoken about and entered. Three decimal places covers rates
    -- like India's 12.5% GST cess without inventing a use for more.
    rate        NUMERIC(6, 3) NOT NULL CHECK (rate >= 0 AND rate <= 100),
    archived_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE TABLE items (
    id                  BIGSERIAL     PRIMARY KEY,
    org_id              BIGINT        NOT NULL REFERENCES organizations(id),
    sku                 VARCHAR(50),
    name                VARCHAR(255)  NOT NULL,
    description         VARCHAR(500),
    default_unit_price  NUMERIC(18, 4),
    default_tax_rate_id BIGINT        REFERENCES tax_rates(id),
    archived_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- Unique per organization, and only while it is actually set -- most catalog
-- items are fine without one, and NULLs must not collide with each other.
CREATE UNIQUE INDEX idx_items_org_sku ON items (org_id, sku) WHERE sku IS NOT NULL;

-- Gapless document numbering: INV-00001, BILL-00001, and so on, one counter
-- per organization per document type. Deliberately not a Postgres SEQUENCE,
-- which advances even when the transaction that read it rolls back -- an
-- accountant reading invoice numbers expects a gap to mean something was
-- voided, not that the database happened to retry a request. Row-level
-- locking one counter row and returning the new value in the same statement
-- (see DocumentNumberingRepository) keeps the increment inside whatever
-- transaction the caller is already in, so a rolled-back posting never
-- consumes a number at all.
CREATE TABLE document_number_counters (
    org_id      BIGINT      NOT NULL REFERENCES organizations(id),
    doc_type    VARCHAR(20) NOT NULL,
    last_number BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (org_id, doc_type)
);

ALTER TABLE contacts ENABLE ROW LEVEL SECURITY;
ALTER TABLE contacts FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON contacts
    USING (org_id = NULLIF(current_setting('app.current_org', true), '')::bigint)
    WITH CHECK (org_id = NULLIF(current_setting('app.current_org', true), '')::bigint);

ALTER TABLE tax_rates ENABLE ROW LEVEL SECURITY;
ALTER TABLE tax_rates FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON tax_rates
    USING (org_id = NULLIF(current_setting('app.current_org', true), '')::bigint)
    WITH CHECK (org_id = NULLIF(current_setting('app.current_org', true), '')::bigint);

ALTER TABLE items ENABLE ROW LEVEL SECURITY;
ALTER TABLE items FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON items
    USING (org_id = NULLIF(current_setting('app.current_org', true), '')::bigint)
    WITH CHECK (org_id = NULLIF(current_setting('app.current_org', true), '')::bigint);

ALTER TABLE document_number_counters ENABLE ROW LEVEL SECURITY;
ALTER TABLE document_number_counters FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON document_number_counters
    USING (org_id = NULLIF(current_setting('app.current_org', true), '')::bigint)
    WITH CHECK (org_id = NULLIF(current_setting('app.current_org', true), '')::bigint);
