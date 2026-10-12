CREATE TABLE finance_transactions (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    request_key VARCHAR(80) NOT NULL,
    transaction_type VARCHAR(16) NOT NULL CHECK (transaction_type IN ('INCOME', 'EXPENSE', 'TRANSFER')),
    amount NUMERIC(19, 2) NOT NULL CHECK (amount > 0),
    currency CHAR(3) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    category VARCHAR(40) NOT NULL,
    merchant TEXT,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_finance_transaction_request UNIQUE (owner_id, request_key)
);

CREATE INDEX idx_finance_transactions_owner_date
    ON finance_transactions (owner_id, occurred_at DESC);

CREATE INDEX idx_finance_transactions_owner_category_date
    ON finance_transactions (owner_id, category, occurred_at DESC);
