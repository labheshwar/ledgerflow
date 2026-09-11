ALTER TABLE accounts
    ADD COLUMN type VARCHAR(10)
        CHECK (type IN ('ASSET', 'LIABILITY', 'EQUITY', 'REVENUE', 'EXPENSE'));

UPDATE accounts SET type = 'ASSET'   WHERE name = 'Cash';
UPDATE accounts SET type = 'ASSET'   WHERE name = 'Accounts Receivable';
UPDATE accounts SET type = 'REVENUE' WHERE name = 'Revenue';

ALTER TABLE accounts ALTER COLUMN type SET NOT NULL;

INSERT INTO accounts (name, currency, balance, type) VALUES
    ('Accounts Payable',   'USD', 0, 'LIABILITY'),
    ('Owner Equity',       'USD', 0, 'EQUITY'),
    ('Operating Expenses', 'USD', 0, 'EXPENSE');
