ALTER TABLE finance_transactions ALTER COLUMN category TYPE VARCHAR(160);

-- Historical values are preserved as entered; unknown payment methods remain NULL.
ALTER TABLE finance_transactions
    ADD COLUMN payment_method VARCHAR(40),
    ADD COLUMN import_source VARCHAR(200),
    ADD COLUMN import_row INTEGER,
    ADD COLUMN import_category VARCHAR(160),
    ADD COLUMN import_fingerprint CHAR(64),
    ADD CONSTRAINT ck_finance_import_metadata CHECK (
        (import_source IS NULL AND import_row IS NULL AND import_category IS NULL AND import_fingerprint IS NULL)
        OR (import_source IS NOT NULL AND import_row IS NOT NULL AND import_row > 0 AND import_category IS NOT NULL
            AND import_fingerprint IS NOT NULL AND import_fingerprint ~ '^[a-f0-9]{64}$')
    );

CREATE UNIQUE INDEX uq_finance_transactions_import_row
    ON finance_transactions (owner_id, import_source, import_row)
    WHERE import_source IS NOT NULL;
