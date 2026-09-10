CREATE TABLE reconciliation_batches (
    id            BIGSERIAL PRIMARY KEY,
    status        VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
                  CHECK (status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'FAILED')),
    triggered_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    completed_at  TIMESTAMPTZ
);

CREATE TABLE reconciliation_results (
    id                BIGSERIAL PRIMARY KEY,
    batch_id          BIGINT        NOT NULL REFERENCES reconciliation_batches(id),
    account_id        BIGINT        NOT NULL REFERENCES accounts(id),
    ledger_balance    NUMERIC(19,4) NOT NULL,
    external_balance  NUMERIC(19,4) NOT NULL,
    status            VARCHAR(10)   NOT NULL CHECK (status IN ('MATCHED', 'MISMATCHED')),
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_reconciliation_results_batch_id ON reconciliation_results(batch_id);
