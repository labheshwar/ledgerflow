-- Multi-tenancy. Every business table gains an org_id; V10 adds the
-- row-level security policies that enforce it.
--
-- Users are deliberately NOT scoped to an org: a username is a global login
-- identity, and org_members is what grants that identity access to a given
-- organization. That is what lets one person belong to several orgs and
-- switch between them.

CREATE TABLE organizations (
    id            BIGSERIAL PRIMARY KEY,
    name          VARCHAR(200) NOT NULL,
    -- VARCHAR rather than CHAR: CHAR is bpchar to the driver, which
    -- schema validation flags against a plain String mapping, and it matches
    -- accounts.currency.
    base_currency VARCHAR(3)   NOT NULL DEFAULT 'USD',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE org_members (
    org_id     BIGINT      NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    user_id    BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role       VARCHAR(20) NOT NULL CHECK (role IN ('ADMIN', 'VIEWER')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (org_id, user_id)
);

CREATE INDEX idx_org_members_user ON org_members (user_id);

-- Everything that exists today belongs to one organization.
INSERT INTO organizations (name, base_currency) VALUES ('Demo Company', 'USD');

-- Role moves from the user to the membership: the same person can be an
-- admin of their own books and a viewer of someone else's.
INSERT INTO org_members (org_id, user_id, role)
SELECT (SELECT id FROM organizations WHERE name = 'Demo Company'), u.id, u.role
FROM users u;

ALTER TABLE users DROP COLUMN role;

ALTER TABLE accounts               ADD COLUMN org_id BIGINT REFERENCES organizations(id);
ALTER TABLE transactions           ADD COLUMN org_id BIGINT REFERENCES organizations(id);
ALTER TABLE entries                ADD COLUMN org_id BIGINT REFERENCES organizations(id);
ALTER TABLE audit_log              ADD COLUMN org_id BIGINT REFERENCES organizations(id);
ALTER TABLE reconciliation_batches ADD COLUMN org_id BIGINT REFERENCES organizations(id);
ALTER TABLE reconciliation_results ADD COLUMN org_id BIGINT REFERENCES organizations(id);

-- audit_log is append-only, enforced by a trigger that rejects UPDATE. The
-- backfill has to go around it, then put it back.
ALTER TABLE audit_log DISABLE TRIGGER audit_log_no_update;

UPDATE accounts               SET org_id = (SELECT id FROM organizations WHERE name = 'Demo Company');
UPDATE transactions           SET org_id = (SELECT id FROM organizations WHERE name = 'Demo Company');
UPDATE entries                SET org_id = (SELECT id FROM organizations WHERE name = 'Demo Company');
UPDATE audit_log              SET org_id = (SELECT id FROM organizations WHERE name = 'Demo Company');
UPDATE reconciliation_batches SET org_id = (SELECT id FROM organizations WHERE name = 'Demo Company');
UPDATE reconciliation_results SET org_id = (SELECT id FROM organizations WHERE name = 'Demo Company');

ALTER TABLE audit_log ENABLE TRIGGER audit_log_no_update;

ALTER TABLE accounts               ALTER COLUMN org_id SET NOT NULL;
ALTER TABLE transactions           ALTER COLUMN org_id SET NOT NULL;
ALTER TABLE entries                ALTER COLUMN org_id SET NOT NULL;
ALTER TABLE audit_log              ALTER COLUMN org_id SET NOT NULL;
ALTER TABLE reconciliation_batches ALTER COLUMN org_id SET NOT NULL;
ALTER TABLE reconciliation_results ALTER COLUMN org_id SET NOT NULL;

-- Every tenant-scoped read filters on org_id, so it leads each index.
CREATE INDEX idx_accounts_org               ON accounts (org_id, name);
CREATE INDEX idx_transactions_org           ON transactions (org_id, created_at DESC);
CREATE INDEX idx_entries_org                ON entries (org_id, account_id);
CREATE INDEX idx_audit_log_org              ON audit_log (org_id, created_at DESC);
CREATE INDEX idx_recon_batches_org          ON reconciliation_batches (org_id, triggered_at DESC);
CREATE INDEX idx_recon_results_org          ON reconciliation_results (org_id, batch_id);

-- A globally unique idempotency key lets one org's client collide with
-- another's and receive a transaction it must never see. Scope it.
ALTER TABLE transactions DROP CONSTRAINT transactions_idempotency_key_key;
ALTER TABLE transactions ADD CONSTRAINT transactions_org_idempotency_key_key
    UNIQUE (org_id, idempotency_key);
