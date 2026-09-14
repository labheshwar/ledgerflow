-- The transactional outbox.
--
-- The problem it solves: a service that writes to Postgres and then calls
-- KafkaTemplate.send() is performing a dual write. If the process dies
-- between the two, or the commit rolls back after the send succeeded, the
-- ledger and the event stream disagree -- and there is no way to tell which
-- one is wrong afterwards. Writing the event as a row in the *same*
-- transaction as the business change makes "the transaction happened" and
-- "the event exists" a single atomic fact. A separate poller then moves
-- rows onto Kafka.
--
-- The cost is at-least-once delivery: a crash after the Kafka send but
-- before the row is marked published will republish it. Consumers must be
-- idempotent -- event_id is what they deduplicate on.

CREATE TABLE outbox_event (
    id             BIGSERIAL PRIMARY KEY,
    -- Stable identity for consumer deduplication. Distinct from id, which is
    -- a delivery-order cursor and would change if a row were ever re-created.
    event_id       UUID         NOT NULL UNIQUE,
    org_id         BIGINT       NOT NULL REFERENCES organizations(id),
    aggregate_type VARCHAR(50)  NOT NULL,
    aggregate_id   VARCHAR(100) NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    topic          VARCHAR(120) NOT NULL,
    partition_key  VARCHAR(200) NOT NULL,
    -- The complete envelope exactly as it will be published, so the poller
    -- is a dumb pipe and cannot drift from what was recorded.
    payload        JSONB        NOT NULL,
    -- W3C trace context captured at write time, replayed as a Kafka header
    -- so one trace spans HTTP request -> outbox -> Kafka -> consumer.
    traceparent    VARCHAR(60),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at   TIMESTAMPTZ,
    attempts       INTEGER      NOT NULL DEFAULT 0,
    last_error     TEXT
);

-- Partial index: the poller only ever asks for unpublished rows, and this
-- keeps its scan proportional to the backlog rather than to the table, which
-- grows forever. Without the WHERE clause the index is as large as the table
-- and the poller degrades as history accumulates.
CREATE INDEX idx_outbox_event_unpublished ON outbox_event (id) WHERE published_at IS NULL;

CREATE INDEX idx_outbox_event_org ON outbox_event (org_id, id);

-- Same fail-closed policy as every other tenant-scoped table. The rows carry
-- tenant data in payload, so the application role must not be able to read
-- another organization's events even though nothing exposes them today.
ALTER TABLE outbox_event ENABLE ROW LEVEL SECURITY;
ALTER TABLE outbox_event FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON outbox_event
    USING (org_id = NULLIF(current_setting('app.current_org', true), '')::bigint)
    WITH CHECK (org_id = NULLIF(current_setting('app.current_org', true), '')::bigint);

-- The third role. The poller has to read every organization's events in one
-- query, which is precisely what the policy above forbids -- so it connects
-- as an identity that bypasses row-level security.
--
-- Kept as narrow as the job allows: SELECT and UPDATE on this one table and
-- nothing else. It cannot INSERT events, cannot touch the ledger, and only
-- the worker process ever opens a connection with it (see OutboxConfig,
-- which is @Profile("worker")). BYPASSRLS is granted, not inherited from
-- superuser, so the role stays powerless everywhere else.
--
-- Note ALTER ROLE ... BYPASSRLS requires the migration to run as a
-- superuser. That is true of the compose stack and of Testcontainers; a
-- deployment whose Flyway identity is merely the table owner would need a
-- DBA to grant this once, out of band.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'ledgerflow_admin') THEN
        CREATE ROLE ledgerflow_admin LOGIN PASSWORD '${admin_password}';
    ELSE
        ALTER ROLE ledgerflow_admin WITH LOGIN PASSWORD '${admin_password}';
    END IF;
END
$$;

ALTER ROLE ledgerflow_admin BYPASSRLS;

GRANT USAGE ON SCHEMA public TO ledgerflow_admin;
GRANT SELECT, UPDATE ON outbox_event TO ledgerflow_admin;
