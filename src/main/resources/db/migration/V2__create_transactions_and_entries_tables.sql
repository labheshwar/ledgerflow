CREATE TABLE transactions (
    id               BIGSERIAL PRIMARY KEY,
    idempotency_key  VARCHAR(255)  NOT NULL UNIQUE,
    description      VARCHAR(500),
    status           VARCHAR(20)   NOT NULL DEFAULT 'POSTED',
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE TABLE entries (
    id              BIGSERIAL PRIMARY KEY,
    transaction_id  BIGINT        NOT NULL REFERENCES transactions(id),
    account_id      BIGINT        NOT NULL REFERENCES accounts(id),
    entry_type      VARCHAR(6)    NOT NULL CHECK (entry_type IN ('DEBIT', 'CREDIT')),
    amount          NUMERIC(19,4) NOT NULL CHECK (amount > 0),
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_entries_account_id ON entries(account_id);
CREATE INDEX idx_entries_transaction_id ON entries(transaction_id);
