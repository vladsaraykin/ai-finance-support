CREATE TABLE users (
    id UUID PRIMARY KEY,
    login VARCHAR(64) NOT NULL UNIQUE CHECK (login ~ '^[a-z0-9][a-z0-9_.-]{2,63}$'),
    password_hash VARCHAR(100) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Existing records retain their owner UUID; bootstrap binds the first login to that UUID.
ALTER TABLE finance_transactions ADD COLUMN deleted_at TIMESTAMPTZ;
CREATE INDEX idx_finance_transactions_active_owner_date
    ON finance_transactions (owner_id, occurred_at DESC) WHERE deleted_at IS NULL;
