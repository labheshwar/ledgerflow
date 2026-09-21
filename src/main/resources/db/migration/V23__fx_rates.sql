-- Milestone 15: a foreign-currency invoice needs a real rate to post at,
-- and settling one at a different rate needs a record of what the rate
-- was at each end. fx_rates holds exactly that -- one row per organization,
-- currency and date, always expressed as "how many units of the org's own
-- base currency equal one unit of this currency" (the same direction
-- Money#convertedTo already expects its own rate argument in).
--
-- No foreign key to organizations.base_currency and no row for the base
-- currency itself: it is always, trivially, 1, and storing it would be one
-- more place that fact could drift from Organization's own column.
CREATE TABLE fx_rates (
    id         BIGSERIAL     PRIMARY KEY,
    org_id     BIGINT        NOT NULL REFERENCES organizations(id),
    currency   VARCHAR(3)    NOT NULL,
    rate       NUMERIC(18,8) NOT NULL CHECK (rate > 0),
    as_of_date DATE          NOT NULL,
    created_at TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- One rate per currency per day -- recording today's rate twice corrects
-- it rather than creating an ambiguous second row for the same day.
CREATE UNIQUE INDEX idx_fx_rates_org_currency_date ON fx_rates (org_id, currency, as_of_date);

-- The lookup this table exists for: the latest rate at or before a given
-- date, for a given currency. DESC on as_of_date is what makes that a
-- straight index scan rather than a sort.
CREATE INDEX idx_fx_rates_lookup ON fx_rates (org_id, currency, as_of_date DESC);

ALTER TABLE fx_rates ENABLE ROW LEVEL SECURITY;
ALTER TABLE fx_rates FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON fx_rates
    USING (org_id = NULLIF(current_setting('app.current_org', true), '')::bigint)
    WITH CHECK (org_id = NULLIF(current_setting('app.current_org', true), '')::bigint);

-- The realized gain/loss leg a foreign-currency settlement posts when the
-- rate at settlement differs from the rate the receivable was booked at.
-- Always in the organization's own base currency -- an FX adjustment has
-- no foreign-currency face value of its own to record, only a base-currency
-- one -- and marked so a report can tell "the business earned this" apart
-- from "the exchange rate moved" without having to infer it from which
-- account the entry happens to be on.
ALTER TABLE entries ADD COLUMN is_fx_adjustment BOOLEAN NOT NULL DEFAULT FALSE;
