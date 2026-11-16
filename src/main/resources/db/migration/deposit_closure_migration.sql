-- Run once in pgAdmin if these two columns/constraints have not already been added.
ALTER TABLE public."MB004007"
    ADD COLUMN IF NOT EXISTS deposit_type VARCHAR(2) NOT NULL DEFAULT 'FD';

ALTER TABLE public."MB004007"
    ADD COLUMN IF NOT EXISTS proceeds_account_no VARCHAR(30);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_mb004007_deposit_type') THEN
        ALTER TABLE public."MB004007"
            ADD CONSTRAINT chk_mb004007_deposit_type
            CHECK (deposit_type IN ('FD', 'RD'));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_mb004007_proceeds_account') THEN
        ALTER TABLE public."MB004007"
            ADD CONSTRAINT fk_mb004007_proceeds_account
            FOREIGN KEY (proceeds_account_no, customer_no)
            REFERENCES public."MB002003" (account_no, customer_no);
    END IF;
END $$;
