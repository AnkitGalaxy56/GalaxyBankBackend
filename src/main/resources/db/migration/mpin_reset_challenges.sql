-- Run once in pgAdmin (Query Tool) on your existing database BEFORE starting the app
-- (application.yaml uses ddl-auto: validate, so the table must exist).
-- Purpose: OTP challenges for "Forgot MPIN" (4-digit OTP).

CREATE TABLE IF NOT EXISTS public.mpin_reset_challenges (
    id          BIGSERIAL PRIMARY KEY,
    customer_id VARCHAR(30) NOT NULL,
    otp_hash    VARCHAR(100) NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    attempts    INTEGER NOT NULL DEFAULT 0,
    verified    BOOLEAN NOT NULL DEFAULT FALSE,
    consumed    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_mpin_reset_attempts CHECK (attempts >= 0 AND attempts <= 5),
    CONSTRAINT fk_mpin_reset_customer FOREIGN KEY (customer_id)
        REFERENCES public.mb001001cr (customer_id)
        ON UPDATE CASCADE ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_mpin_reset_customer
    ON public.mpin_reset_challenges (customer_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_mpin_reset_expiry
    ON public.mpin_reset_challenges (expires_at);

-- If your app connects with a restricted DB user, also run (replace the user name):
-- GRANT SELECT, INSERT, UPDATE ON public.mpin_reset_challenges TO abcbank_app;
-- GRANT USAGE, SELECT ON SEQUENCE public.mpin_reset_challenges_id_seq TO abcbank_app;
-- GRANT SELECT, UPDATE ON public."MB001001", public."MB001003" TO abcbank_app;
-- GRANT SELECT ON public."MB001002" TO abcbank_app;
