-- Confirm with your bank's existing customer master before applying.
-- Seed customer_registry from a trusted core-banking source; do NOT accept client-created customer/mobile pairs.
CREATE TABLE IF NOT EXISTS customer_registry (
    customer_id VARCHAR(30) PRIMARY KEY,
    registered_mobile VARCHAR(20) NOT NULL,
    eligible_for_mobile_banking BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT chk_customer_mobile CHECK (registered_mobile ~ '^[6-9][0-9]{9}$')
);

CREATE TABLE IF NOT EXISTS registration_challenges (
    id BIGSERIAL PRIMARY KEY,
    customer_id VARCHAR(30) NOT NULL REFERENCES customer_registry(customer_id),
    otp_hash VARCHAR(100) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0 AND attempts <= 5),
    consumed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_reg_challenge_customer ON registration_challenges(customer_id, created_at DESC);
