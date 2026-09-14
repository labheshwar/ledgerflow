-- Tenant isolation enforced by the database rather than by remembering to
-- write WHERE org_id = ? in every query.
--
-- Two roles from here on:
--   * the migration/owner role (whoever Flyway connects as) owns the tables
--   * ledgerflow_app is what the running application connects as
--
-- This split is load-bearing: a superuser bypasses row-level security
-- entirely, and FORCE ROW LEVEL SECURITY does not apply to them. If the app
-- kept connecting as the owning superuser, every policy below would be
-- decorative and the isolation tests would pass while protecting nothing.

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'ledgerflow_app') THEN
        CREATE ROLE ledgerflow_app LOGIN PASSWORD '${app_password}';
    ELSE
        ALTER ROLE ledgerflow_app WITH LOGIN PASSWORD '${app_password}';
    END IF;
END
$$;

GRANT USAGE ON SCHEMA public TO ledgerflow_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO ledgerflow_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO ledgerflow_app;

-- Tables created by later migrations are granted automatically, so a new
-- table can't silently be unreadable by the application.
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO ledgerflow_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO ledgerflow_app;

-- Two details make this fail closed rather than blow up or leak.
--
-- current_setting(..., true) returns NULL instead of raising when the GUC was
-- never set. NULL = org_id is NULL, not true, so a request that forgot to
-- establish tenant context sees zero rows rather than every row.
--
-- NULLIF(..., '') is not redundant. Once set_config has run on a pooled
-- connection the parameter exists on that session, and a transaction-scoped
-- value reverts at commit to the empty string -- not to NULL. Casting ''
-- to bigint raises "invalid input syntax for type bigint", which would turn
-- every unscoped query on a recycled connection into a 500 instead of an
-- empty result.
--
-- FORCE is what makes the policy apply to the table owner too.
DO $$
DECLARE
    t TEXT;
BEGIN
    FOREACH t IN ARRAY ARRAY[
        'accounts',
        'transactions',
        'entries',
        'audit_log',
        'reconciliation_batches',
        'reconciliation_results'
    ]
    LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
        EXECUTE format(
            'CREATE POLICY tenant_isolation ON %I'
            || ' USING (org_id = NULLIF(current_setting(''app.current_org'', true), '''')::bigint)'
            || ' WITH CHECK (org_id = NULLIF(current_setting(''app.current_org'', true), '''')::bigint)', t);
    END LOOP;
END
$$;
