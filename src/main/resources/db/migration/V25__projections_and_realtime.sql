-- Milestone 16: read models kept in step with the ledger by consuming
-- ledger.transactions.v1, plus the plumbing idempotent consumption and the
-- SSE backfill both need.
--
-- Every one of these tables is rebuilt by absolute recompute from source
-- (invoices, bills, payments, entries), never by incremental arithmetic on
-- the previous row. That is what makes "kill the projector, replay, get
-- identical numbers" true by construction rather than by care: replaying
-- the same event once or a hundred times converges on the same row, and
-- rebuilding from a cold, empty table is the same code path as an ordinary
-- update.

-- One row per organization: the numbers DashboardController now serves
-- straight from, instead of recomputing the aggregate on every request.
CREATE TABLE dashboard_metrics (
    org_id             BIGINT      PRIMARY KEY REFERENCES organizations(id),
    account_count      BIGINT      NOT NULL,
    total_ledger_balance NUMERIC(18,4) NOT NULL,
    postings_today     BIGINT      NOT NULL,
    open_ar_total      NUMERIC(18,4) NOT NULL,
    overdue_ar_total   NUMERIC(18,4) NOT NULL,
    open_ap_total      NUMERIC(18,4) NOT NULL,
    overdue_ap_total   NUMERIC(18,4) NOT NULL,
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE dashboard_metrics ENABLE ROW LEVEL SECURITY;
ALTER TABLE dashboard_metrics FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON dashboard_metrics
    USING (org_id = NULLIF(current_setting('app.current_org', true), '')::bigint)
    WITH CHECK (org_id = NULLIF(current_setting('app.current_org', true), '')::bigint);

-- One row per still-open invoice. Rebuilt wholesale for the organization
-- every time -- a paid or voided invoice simply stops being written back,
-- rather than being deleted by a separate code path that could disagree
-- with this one about what "still open" means.
CREATE TABLE ar_aging (
    org_id         BIGINT        NOT NULL REFERENCES organizations(id),
    invoice_id     BIGINT        NOT NULL,
    contact_id     BIGINT        NOT NULL,
    contact_name   VARCHAR(200)  NOT NULL,
    invoice_number VARCHAR(20),
    due_date       DATE          NOT NULL,
    currency       VARCHAR(3)    NOT NULL,
    balance        NUMERIC(18,4) NOT NULL,
    bucket         VARCHAR(12)   NOT NULL,
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    PRIMARY KEY (org_id, invoice_id)
);

CREATE INDEX idx_ar_aging_org_bucket ON ar_aging (org_id, bucket);

ALTER TABLE ar_aging ENABLE ROW LEVEL SECURITY;
ALTER TABLE ar_aging FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON ar_aging
    USING (org_id = NULLIF(current_setting('app.current_org', true), '')::bigint)
    WITH CHECK (org_id = NULLIF(current_setting('app.current_org', true), '')::bigint);

-- The same shape, for still-open bills.
CREATE TABLE ap_aging (
    org_id         BIGINT        NOT NULL REFERENCES organizations(id),
    bill_id        BIGINT        NOT NULL,
    contact_id     BIGINT        NOT NULL,
    contact_name   VARCHAR(200)  NOT NULL,
    bill_number    VARCHAR(20),
    due_date       DATE          NOT NULL,
    currency       VARCHAR(3)    NOT NULL,
    balance        NUMERIC(18,4) NOT NULL,
    bucket         VARCHAR(12)   NOT NULL,
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    PRIMARY KEY (org_id, bill_id)
);

CREATE INDEX idx_ap_aging_org_bucket ON ap_aging (org_id, bucket);

ALTER TABLE ap_aging ENABLE ROW LEVEL SECURITY;
ALTER TABLE ap_aging FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON ap_aging
    USING (org_id = NULLIF(current_setting('app.current_org', true), '')::bigint)
    WITH CHECK (org_id = NULLIF(current_setting('app.current_org', true), '')::bigint);

-- Idempotent consumption: an at-least-once redelivery of an event this
-- consumer group already applied is a no-op, detected by the primary key
-- rather than re-derived from whatever the projection tables currently
-- contain. org_id is carried for row-level security, not for the uniqueness
-- itself -- event_id is already a UUID, globally unique on its own.
CREATE TABLE processed_event (
    consumer_group VARCHAR(100) NOT NULL,
    event_id       UUID         NOT NULL,
    org_id         BIGINT       NOT NULL REFERENCES organizations(id),
    processed_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (consumer_group, event_id)
);

ALTER TABLE processed_event ENABLE ROW LEVEL SECURITY;
ALTER TABLE processed_event FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON processed_event
    USING (org_id = NULLIF(current_setting('app.current_org', true), '')::bigint)
    WITH CHECK (org_id = NULLIF(current_setting('app.current_org', true), '')::bigint);
