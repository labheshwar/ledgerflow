-- Same backfill V20 did for Customer/Vendor Prepayments, for the same
-- reason: every organization created since DefaultChartOfAccounts grew
-- this seed entry gets a Foreign Exchange Gain/Loss account automatically,
-- but the demo organization predates it and has never had one. Its own
-- seed comment ("nothing posts here until exchange rates exist") is
-- exactly why this backfill waited for milestone 15 rather than running
-- alongside V20 -- there was nothing to post until now.
--
-- RLS is disabled around it for the same reason V20's own backfill
-- disables it: this INSERT spans every organization at once, and a
-- migration has no tenant in context for the policy to compare against.
ALTER TABLE accounts DISABLE ROW LEVEL SECURITY;

INSERT INTO accounts (org_id, code, name, type, currency, system_role, is_postable, created_at, updated_at)
SELECT
    o.id,
    CASE WHEN EXISTS (SELECT 1 FROM accounts a2 WHERE a2.org_id = o.id AND a2.code = '7000')
         THEN 'FXGL-' || o.id::text
         ELSE '7000' END,
    'Foreign Exchange Gain/Loss',
    'EXPENSE',
    o.base_currency,
    'FX_GAIN_LOSS',
    TRUE,
    now(),
    now()
FROM organizations o
WHERE NOT EXISTS (
    SELECT 1 FROM accounts a WHERE a.org_id = o.id AND a.system_role = 'FX_GAIN_LOSS'
);

ALTER TABLE accounts ENABLE ROW LEVEL SECURITY;
