BEGIN;

-- Allow closure requests without a separate FD/RD account number.
ALTER TABLE public."MB004007"
    ALTER COLUMN fd_acid DROP NOT NULL;

-- Remove the incorrect FD/RD account-master foreign key.
ALTER TABLE public."MB004007"
    DROP CONSTRAINT IF EXISTS fk_mb004007_fd;

-- Store the actual FD/RD opening request ID.
ALTER TABLE public."MB004007"
    ADD COLUMN IF NOT EXISTS deposit_opening_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_mb004007_deposit_opening
    ON public."MB004007"
       (customer_no, deposit_type, deposit_opening_id);

COMMIT;