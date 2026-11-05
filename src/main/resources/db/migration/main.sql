

-- ---------- 0. Reusable domains (change a rule in ONE place) ----------
DO $$ BEGIN
  CREATE DOMAIN req_status_t AS VARCHAR(20)
    CHECK (VALUE IN ('PENDING','APPROVED','REJECTED','CANCELLED','COMPLETED','FAILED'));
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
  CREATE DOMAIN ifsc_t AS VARCHAR(11) CHECK (VALUE ~ '^[A-Z]{4}0[A-Z0-9]{6}$');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
  CREATE DOMAIN amount_t AS NUMERIC(18,2) CHECK (VALUE > 0);
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- ---------- 1. Customer registry (from app: mb001001cr) --------
-- Seeded ONLY from core banking. Never from the client.
CREATE TABLE IF NOT EXISTS public.mb001001cr (
    customer_id                  VARCHAR(30) PRIMARY KEY,
    eligible_for_mobile_banking  BOOLEAN     NOT NULL DEFAULT TRUE,
    registered_mobile            VARCHAR(20) NOT NULL,
    CONSTRAINT chk_customer_mobile CHECK (registered_mobile ~ '^[6-9][0-9]{9}$')
);

-- ---------- 2. BASE TABLE MB001001  Usermaster ------
-- NOTE: your file created "Usermaster" but MB001002/3 point to "MB001001".
CREATE TABLE IF NOT EXISTS public."MB001001" (
    user_id BIGSERIAL PRIMARY KEY,

    customer_no VARCHAR(30) UNIQUE,
    emp_no VARCHAR(30) UNIQUE,

    title VARCHAR(10)
        CHECK (title IN ('Mr.', 'Mrs.', 'Miss', 'Ms.', 'Dr.')),

    user_type VARCHAR(10) NOT NULL
        CHECK (user_type IN ('CU', 'STAFF', 'ADMIN')),

    first_name VARCHAR(80) NOT NULL,
    middle_name VARCHAR(80),
    last_name VARCHAR(80),

    full_name VARCHAR(250)
        GENERATED ALWAYS AS (
            COALESCE(title || ' ', '') ||
            first_name ||
            COALESCE(' ' || NULLIF(middle_name, ''), '') ||
            COALESCE(' ' || NULLIF(last_name, ''), '')
        ) STORED,

    designation VARCHAR(100),
    dob DATE,
    email VARCHAR(254),
    mobile VARCHAR(20),

    user_name VARCHAR(80) NOT NULL UNIQUE,
    disp_name VARCHAR(100),

    user_role VARCHAR(50),
    sys_role VARCHAR(50),
    branch VARCHAR(100),

    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'ACTIVE', 'LOCKED', 'CLOSED')),

    multi_branch BOOLEAN NOT NULL DEFAULT FALSE,
    idle_timeout SMALLINT,

    bad_login SMALLINT NOT NULL DEFAULT 0
        CHECK (bad_login >= 0),

    login_time TIMESTAMPTZ,
    logout_time TIMESTAMPTZ,
    inop_date DATE,

    "2fa_login" BOOLEAN NOT NULL DEFAULT TRUE,
    authentication VARCHAR(50),

    created_by VARCHAR(80),
    created_on TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified_by VARCHAR(80),
    modified_on TIMESTAMPTZ,
    verified_by VARCHAR(80),
    verified_on TIMESTAMPTZ
);

-- Added links on the base table:
--  * a customer user must exist in the registry (staff keep customer_no NULL)
--  * customer users carry customer_no, staff/admin carry emp_no
DO $$ BEGIN
  ALTER TABLE public."MB001001"
    ADD CONSTRAINT fk_mb001001_customer
    FOREIGN KEY (customer_no) REFERENCES public.mb001001cr(customer_id)
    ON UPDATE CASCADE ON DELETE RESTRICT;
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
  ALTER TABLE public."MB001001"
    ADD CONSTRAINT chk_mb001001_identity CHECK (
      (user_type = 'CU' AND customer_no IS NOT NULL)
      OR (user_type <> 'CU' AND emp_no IS NOT NULL));
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- ---------- 3. BASE TABLE MB001002  Password (your DDL, unchanged) ----
CREATE TABLE IF NOT EXISTS public."MB001002" (
    password_id BIGSERIAL PRIMARY KEY,

    user_id BIGINT NOT NULL UNIQUE,

    password_hash VARCHAR(255) NOT NULL,
    password_changed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    must_reset BOOLEAN NOT NULL DEFAULT TRUE,

    created_by BIGINT,
    created_on TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified_by BIGINT,
    modified_on TIMESTAMPTZ,

    CONSTRAINT fk_mb001002_usermaster
        FOREIGN KEY (user_id)
        REFERENCES public."MB001001" (user_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT
);

-- ---------- 4. BASE TABLE MB001003  MPIN (your DDL, unchanged) --------
CREATE TABLE IF NOT EXISTS public."MB001003" (
    mpin_id BIGSERIAL PRIMARY KEY,

    -- Connects this MPIN record to Usermaster
    user_id BIGINT NOT NULL UNIQUE,

    -- Store only a secure hash of the MPIN
    mpin_hash VARCHAR(255) NOT NULL,

    pin_changed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    failed_attempts INTEGER NOT NULL DEFAULT 0,
    locked_until TIMESTAMPTZ,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

    created_by BIGINT,
    created_on TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified_by BIGINT,
    modified_on TIMESTAMPTZ,

    CONSTRAINT fk_mb001003_usermaster
        FOREIGN KEY (user_id)
        REFERENCES public."MB001001" (user_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT chk_mb001003_failed_attempts
        CHECK (failed_attempts >= 0),

    CONSTRAINT chk_mb001003_status
        CHECK (status IN ('ACTIVE', 'LOCKED', 'INACTIVE'))
);

-- ---------- 5. Registration flow (from app) ---------------------------
-- Enrolment record: which customer has a mobile-banking login.
-- The MPIN hash lives ONLY in MB001003 (single source of truth).
CREATE TABLE IF NOT EXISTS public.mobile_banking_users (
    customer_id         VARCHAR(30) PRIMARY KEY,
    user_id             BIGINT      NOT NULL UNIQUE,
    registration_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (registration_status IN ('ACTIVE','LOCKED','CLOSED')),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_mbu_customer FOREIGN KEY (customer_id)
        REFERENCES public.mb001001cr(customer_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_mbu_user FOREIGN KEY (user_id)
        REFERENCES public."MB001001"(user_id) ON UPDATE CASCADE ON DELETE RESTRICT
);

CREATE TABLE IF NOT EXISTS public.registration_challenges (
    id          BIGSERIAL PRIMARY KEY,
    customer_id VARCHAR(30) NOT NULL,
    otp_hash    VARCHAR(100) NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    attempts    INTEGER NOT NULL DEFAULT 0,
    consumed    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_registration_challenges_customer FOREIGN KEY (customer_id)
        REFERENCES public.mb001001cr(customer_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT chk_registration_challenge_attempts CHECK (attempts >= 0 AND attempts <= 5)
);
CREATE INDEX IF NOT EXISTS idx_reg_challenge_customer ON public.registration_challenges (customer_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_reg_challenge_expiry   ON public.registration_challenges (expires_at);
CREATE INDEX IF NOT EXISTS idx_reg_challenge_active   ON public.registration_challenges (customer_id, consumed, expires_at);

-- ---------- 6. Setup masters (MB002xxx) --------------------------------
-- MB002001 Bank Master  (Excel column "add" is a reserved word -> "address")
CREATE TABLE IF NOT EXISTS public."MB002001" (
    bank_code         VARCHAR(20) PRIMARY KEY,
    bank_name         VARCHAR(150) NOT NULL,
    bank_type         VARCHAR(30),
    rbi_license       VARCHAR(50),
    sponsor_bank      VARCHAR(20),
    ifsc_code         ifsc_t UNIQUE,
    account_no        VARCHAR(30),
    pan_no            VARCHAR(10) CHECK (pan_no ~ '^[A-Z]{5}[0-9]{4}[A-Z]$'),
    gst_no            VARCHAR(15) CHECK (gst_no ~ '^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$'),
    working_time_from TIME,
    working_time_to   TIME,
    micr_code         VARCHAR(9) CHECK (micr_code ~ '^[0-9]{9}$'),
    address           VARCHAR(150),
    add1              VARCHAR(150),
    add2              VARCHAR(150),
    add3              VARCHAR(150),
    city              VARCHAR(80),
    state             VARCHAR(80),
    pincode           VARCHAR(6) CHECK (pincode ~ '^[1-9][0-9]{5}$'),
    created_by  BIGINT,
    created_on  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified_by BIGINT,
    modified_on TIMESTAMPTZ,
    verified_by BIGINT,
    verified_on TIMESTAMPTZ,
    CONSTRAINT fk_mb002001_sponsor  FOREIGN KEY (sponsor_bank) REFERENCES public."MB002001"(bank_code) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_mb002001_created  FOREIGN KEY (created_by)  REFERENCES public."MB001001"(user_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_mb002001_modified FOREIGN KEY (modified_by) REFERENCES public."MB001001"(user_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_mb002001_verified FOREIGN KEY (verified_by) REFERENCES public."MB001001"(user_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT chk_mb002001_hours CHECK (working_time_from IS NULL OR working_time_to IS NULL OR working_time_from < working_time_to)
);

-- MB002002 Branch Master  (bank_code added so each branch belongs to a bank)
CREATE TABLE IF NOT EXISTS public."MB002002" (
    branch_code  VARCHAR(20) PRIMARY KEY,
    bank_code    VARCHAR(20) NOT NULL,
    branch_name  VARCHAR(150) NOT NULL,
    rbi_license  VARCHAR(50),
    micr_code    VARCHAR(9) CHECK (micr_code ~ '^[0-9]{9}$'),
    ifsc_code    ifsc_t NOT NULL UNIQUE,
    br_add       VARCHAR(150),
    br_add1      VARCHAR(150),
    br_add2      VARCHAR(150),
    br_add3      VARCHAR(150),
    br_city      VARCHAR(80),
    br_state     VARCHAR(80),
    br_pincode   VARCHAR(6) CHECK (br_pincode ~ '^[1-9][0-9]{5}$'),
    created_by  BIGINT,
    created_on  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified_by BIGINT,
    modified_on TIMESTAMPTZ,
    verified_by BIGINT,
    verified_on TIMESTAMPTZ,
    CONSTRAINT fk_mb002002_bank     FOREIGN KEY (bank_code)   REFERENCES public."MB002001"(bank_code) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_mb002002_created  FOREIGN KEY (created_by)  REFERENCES public."MB001001"(user_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_mb002002_modified FOREIGN KEY (modified_by) REFERENCES public."MB001001"(user_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_mb002002_verified FOREIGN KEY (verified_by) REFERENCES public."MB001001"(user_id) ON UPDATE CASCADE ON DELETE RESTRICT
);
CREATE INDEX IF NOT EXISTS idx_mb002002_bank ON public."MB002002"(bank_code);

-- MB002003 Account Master (NEW) - mirror of core-banking accounts.
-- Every request table points here, so a customer can only act on own accounts.
CREATE TABLE IF NOT EXISTS public."MB002003" (
    account_no     VARCHAR(30) PRIMARY KEY,
    customer_no    VARCHAR(30) NOT NULL,
    branch_code    VARCHAR(20) NOT NULL,
    account_type   VARCHAR(20) NOT NULL,   -- e.g. SAVINGS, CURRENT, FD, RD, LOAN
    account_status VARCHAR(20) NOT NULL,   -- e.g. ACTIVE, DORMANT, CLOSED
    created_on     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified_on    TIMESTAMPTZ,
    synced_on      TIMESTAMPTZ,
    CONSTRAINT uq_mb002003_account_customer UNIQUE (account_no, customer_no),
    CONSTRAINT fk_mb002003_customer FOREIGN KEY (customer_no) REFERENCES public.mb001001cr(customer_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_mb002003_branch   FOREIGN KEY (branch_code) REFERENCES public."MB002002"(branch_code) ON UPDATE CASCADE ON DELETE RESTRICT
);
CREATE INDEX IF NOT EXISTS idx_mb002003_customer ON public."MB002003"(customer_no);

-- ---------- 7. Notification & device (MB003xxx) ------------------------
CREATE TABLE IF NOT EXISTS public."MB003001" (
    id           BIGSERIAL PRIMARY KEY,
    branch_code  VARCHAR(20),
    customer_no  VARCHAR(30) NOT NULL,
    notification TEXT NOT NULL,
    is_read      BOOLEAN NOT NULL DEFAULT FALSE,           -- added
    created_on   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, -- added
    CONSTRAINT fk_mb003001_branch   FOREIGN KEY (branch_code) REFERENCES public."MB002002"(branch_code) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_mb003001_customer FOREIGN KEY (customer_no) REFERENCES public.mb001001cr(customer_id) ON UPDATE CASCADE ON DELETE RESTRICT
);
CREATE INDEX IF NOT EXISTS idx_mb003001_customer_unread ON public."MB003001"(customer_no, is_read, created_on DESC);

CREATE TABLE IF NOT EXISTS public."MB003002" (
    id          BIGSERIAL PRIMARY KEY,
    customer_no VARCHAR(30) NOT NULL,
    mobile      VARCHAR(20) NOT NULL CHECK (mobile ~ '^[6-9][0-9]{9}$'),
    email       VARCHAR(254),
    device_id   VARCHAR(100),                              -- added
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,             -- added
    created_by  BIGINT NOT NULL,
    created_on  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified_by BIGINT,
    modified_on TIMESTAMPTZ,
    verified_by BIGINT,
    verified_on TIMESTAMPTZ,
    CONSTRAINT fk_mb003002_customer FOREIGN KEY (customer_no) REFERENCES public.mb001001cr(customer_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_mb003002_created  FOREIGN KEY (created_by)  REFERENCES public."MB001001"(user_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_mb003002_modified FOREIGN KEY (modified_by) REFERENCES public."MB001001"(user_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_mb003002_verified FOREIGN KEY (verified_by) REFERENCES public."MB001001"(user_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT chk_mb003002_maker_checker CHECK (verified_by IS NULL OR verified_by <> created_by),
    CONSTRAINT chk_mb003002_verify_pair   CHECK ((verified_by IS NULL) = (verified_on IS NULL))
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_mb003002_active_device ON public."MB003002"(customer_no, device_id) WHERE is_active AND device_id IS NOT NULL;

-- ---------- 8. Customer service requests (MB004xxx) --------------------
-- Every table: branch -> MB002002, (account,customer) -> MB002003,
-- created/modified/verified_by -> MB001001, maker <> checker.

-- MB004001  Remittance
CREATE TABLE IF NOT EXISTS public."MB004001" (
    id            BIGSERIAL PRIMARY KEY,
    branch_code   VARCHAR(20) NOT NULL,
    customer_no   VARCHAR(30) NOT NULL,
    account_no    VARCHAR(30) NOT NULL,
    account_status VARCHAR(20),   -- snapshot at request time
    account_type   VARCHAR(20),   -- snapshot at request time
    req_status   req_status_t NOT NULL DEFAULT 'PENDING',
    ifsc_code     ifsc_t,
    pay_mode      VARCHAR(20) NOT NULL,       -- NEFT / RTGS / IMPS ...
    ben_name      VARCHAR(150) NOT NULL,
    ben_account   VARCHAR(34) NOT NULL,
    ben_bank      VARCHAR(150),
    ben_ifsc      ifsc_t NOT NULL,
    amount        amount_t NOT NULL,
    remarks       VARCHAR(255),
    created_by   BIGINT       NOT NULL,
    created_on   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified_by  BIGINT,
    modified_on  TIMESTAMPTZ,
    verified_by  BIGINT,
    verified_on  TIMESTAMPTZ,
    CONSTRAINT fk_mb004001_branch FOREIGN KEY (branch_code) REFERENCES public."MB002002"(branch_code) ON UPDATE CASCADE ON DELETE RESTRICT,
    -- composite FK: the account must belong to THIS customer
    CONSTRAINT fk_mb004001_account FOREIGN KEY (account_no, customer_no) REFERENCES public."MB002003"(account_no, customer_no) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_mb004001_created  FOREIGN KEY (created_by)  REFERENCES public."MB001001"(user_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_mb004001_modified FOREIGN KEY (modified_by) REFERENCES public."MB001001"(user_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_mb004001_verified FOREIGN KEY (verified_by) REFERENCES public."MB001001"(user_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT chk_mb004001_maker_checker CHECK (verified_by IS NULL OR verified_by <> created_by),
    CONSTRAINT chk_mb004001_verify_pair   CHECK ((verified_by IS NULL) = (verified_on IS NULL))
);

CREATE INDEX IF NOT EXISTS idx_mb004001_customer ON public."MB004001" (customer_no, created_on DESC);
CREATE INDEX IF NOT EXISTS idx_mb004001_branch_status ON public."MB004001" (branch_code, req_status);

-- MB004002  Cheque Book
CREATE TABLE IF NOT EXISTS public."MB004002" (
    id            BIGSERIAL PRIMARY KEY,
    branch_code   VARCHAR(20) NOT NULL,
    customer_no   VARCHAR(30) NOT NULL,
    account_no    VARCHAR(30) NOT NULL,
    account_status VARCHAR(20),   -- snapshot at request time
    account_type   VARCHAR(20),   -- snapshot at request time
    req_status   req_status_t NOT NULL DEFAULT 'PENDING',
    noof_books    SMALLINT NOT NULL CHECK (noof_books > 0),
    delivery      VARCHAR(20) NOT NULL        -- BRANCH / HOME ...,
    created_by   BIGINT       NOT NULL,
    created_on   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified_by  BIGINT,
    modified_on  TIMESTAMPTZ,
    verified_by  BIGINT,
    verified_on  TIMESTAMPTZ,
    CONSTRAINT fk_mb004002_branch FOREIGN KEY (branch_code) REFERENCES public."MB002002"(branch_code) ON UPDATE CASCADE ON DELETE RESTRICT,
    -- composite FK: the account must belong to THIS customer
    CONSTRAINT fk_mb004002_account FOREIGN KEY (account_no, customer_no) REFERENCES public."MB002003"(account_no, customer_no) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_mb004002_created  FOREIGN KEY (created_by)  REFERENCES public."MB001001"(user_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_mb004002_modified FOREIGN KEY (modified_by) REFERENCES public."MB001001"(user_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_mb004002_verified FOREIGN KEY (verified_by) REFERENCES public."MB001001"(user_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT chk_mb004002_maker_checker CHECK (verified_by IS NULL OR verified_by <> created_by),
    CONSTRAINT chk_mb004002_verify_pair   CHECK ((verified_by IS NULL) = (verified_on IS NULL))
);

CREATE INDEX IF NOT EXISTS idx_mb004002_customer ON public."MB004002" (customer_no, created_on DESC);
CREATE INDEX IF NOT EXISTS idx_mb004002_branch_status ON public."MB004002" (branch_code, req_status);

-- MB004003  Stop Payment
CREATE TABLE IF NOT EXISTS public."MB004003" (
    id            BIGSERIAL PRIMARY KEY,
    branch_code   VARCHAR(20) NOT NULL,
    customer_no   VARCHAR(30) NOT NULL,
    account_no    VARCHAR(30) NOT NULL,
    account_status VARCHAR(20),   -- snapshot at request time
    account_type   VARCHAR(20),   -- snapshot at request time
    req_status   req_status_t NOT NULL DEFAULT 'PENDING',
    cheque_no     VARCHAR(20) NOT NULL,
    cheque_date   DATE,
    issued_to     VARCHAR(150),
    cheque_amount amount_t,
    reason        VARCHAR(255),
    created_by   BIGINT       NOT NULL,
    created_on   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified_by  BIGINT,
    modified_on  TIMESTAMPTZ,
    verified_by  BIGINT,
    verified_on  TIMESTAMPTZ,
    CONSTRAINT fk_mb004003_branch FOREIGN KEY (branch_code) REFERENCES public."MB002002"(branch_code) ON UPDATE CASCADE ON DELETE RESTRICT,
    -- composite FK: the account must belong to THIS customer
    CONSTRAINT fk_mb004003_account FOREIGN KEY (account_no, customer_no) REFERENCES public."MB002003"(account_no, customer_no) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_mb004003_created  FOREIGN KEY (created_by)  REFERENCES public."MB001001"(user_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_mb004003_modified FOREIGN KEY (modified_by) REFERENCES public."MB001001"(user_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_mb004003_verified FOREIGN KEY (verified_by) REFERENCES public."MB001001"(user_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT chk_mb004003_maker_checker CHECK (verified_by IS NULL OR verified_by <> created_by),
    CONSTRAINT chk_mb004003_verify_pair   CHECK ((verified_by IS NULL) = (verified_on IS NULL))
);

CREATE INDEX IF NOT EXISTS idx_mb004003_customer ON public."MB004003" (customer_no, created_on DESC);
CREATE INDEX IF NOT EXISTS idx_mb004003_branch_status ON public."MB004003" (branch_code, req_status);
-- one live stop-payment per cheque
CREATE UNIQUE INDEX IF NOT EXISTS uq_mb004003_live_cheque ON public."MB004003"(account_no, cheque_no)
    WHERE  IN ('PENDING','APPROVED','COMPLETED');

-- MB004004  FD Opening
CREATE TABLE IF NOT EXISTS public."MB004004" (
    id            BIGSERIAL PRIMARY KEY,
    branch_code   VARCHAR(20) NOT NULL,
    customer_no   VARCHAR(30) NOT NULL,
    account_no    VARCHAR(30) NOT NULL,
    debit_account_status VARCHAR(20),   -- snapshot at request time
    debit_account_type   VARCHAR(20),   -- snapshot at request time
    req_status   req_status_t NOT NULL DEFAULT 'PENDING',
    prod_code     VARCHAR(20) NOT NULL,
    prod_name     VARCHAR(100),
    scheme_code   VARCHAR(20) NOT NULL,
    scheme_name   VARCHAR(100),
    cert_date     DATE,
    asof_date     DATE NOT NULL,
    deposit_amount amount_t NOT NULL,
    int_rate      NUMERIC(7,4) NOT NULL CHECK (int_rate >= 0),
    offset_rate   NUMERIC(7,4),
    tenure        INTEGER NOT NULL CHECK (tenure > 0),   -- months
    auto_renewal  BOOLEAN NOT NULL DEFAULT FALSE,
    maturity_date DATE,
    maturity_value NUMERIC(18,2),
    cr_int_acid   VARCHAR(30),
    maturity_acid VARCHAR(30),
    renew_int     BOOLEAN NOT NULL DEFAULT FALSE,
    created_by   BIGINT       NOT NULL,
    created_on   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified_by  BIGINT,
    modified_on  TIMESTAMPTZ,
    verified_by  BIGINT,
    verified_on  TIMESTAMPTZ,
    CONSTRAINT fk_mb004004_branch FOREIGN KEY (branch_code) REFERENCES public."MB002002"(branch_code) ON UPDATE CASCADE ON DELETE RESTRICT,
    -- composite FK: the account must belong to THIS customer
    CONSTRAINT fk_mb004004_account FOREIGN KEY (account_no, customer_no) REFERENCES public."MB002003"(account_no, customer_no) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT chk_mb004004_dates CHECK (maturity_date IS NULL OR maturity_date > asof_date),
    CONSTRAINT fk_mb004004_created  FOREIGN KEY (created_by)  REFERENCES public."MB001001"(user_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_mb004004_modified FOREIGN KEY (modified_by) REFERENCES public."MB001001"(user_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_mb004004_verified FOREIGN KEY (verified_by) REFERENCES public."MB001001"(user_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT chk_mb004004_maker_checker CHECK (verified_by IS NULL OR verified_by <> created_by),
    CONSTRAINT chk_mb004004_verify_pair   CHECK ((verified_by IS NULL) = (verified_on IS NULL))
);

CREATE INDEX IF NOT EXISTS idx_mb004004_customer ON public."MB004004" (customer_no, created_on DESC);
CREATE INDEX IF NOT EXISTS idx_mb004004_branch_status ON public."MB004004" (branch_code, req_status);

-- MB004005  RD Opening - deposit_amount = monthly instalment
CREATE TABLE IF NOT EXISTS public."MB004005" (
    id            BIGSERIAL PRIMARY KEY,
    branch_code   VARCHAR(20) NOT NULL,
    customer_no   VARCHAR(30) NOT NULL,
    account_no    VARCHAR(30) NOT NULL,
    debit_account_status VARCHAR(20),   -- snapshot at request time
    debit_account_type   VARCHAR(20),   -- snapshot at request time
    req_status   req_status_t NOT NULL DEFAULT 'PENDING',
    prod_code     VARCHAR(20) NOT NULL,
    prod_name     VARCHAR(100),
    scheme_code   VARCHAR(20) NOT NULL,
    scheme_name   VARCHAR(100),
    cert_date     DATE,
    asof_date     DATE NOT NULL,
    deposit_amount amount_t NOT NULL,
    int_rate      NUMERIC(7,4) NOT NULL CHECK (int_rate >= 0),
    offset_rate   NUMERIC(7,4),
    tenure        INTEGER NOT NULL CHECK (tenure > 0),   -- months
    auto_renewal  BOOLEAN NOT NULL DEFAULT FALSE,
    maturity_date DATE,
    maturity_value NUMERIC(18,2),
    cr_int_acid   VARCHAR(30),
    maturity_acid VARCHAR(30),
    created_by   BIGINT       NOT NULL,
    created_on   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified_by  BIGINT,
    modified_on  TIMESTAMPTZ,
    verified_by  BIGINT,
    verified_on  TIMESTAMPTZ,
    CONSTRAINT fk_mb004005_branch FOREIGN KEY (branch_code) REFERENCES public."MB002002"(branch_code) ON UPDATE CASCADE ON DELETE RESTRICT,
    -- composite FK: the account must belong to THIS customer
    CONSTRAINT fk_mb004005_account FOREIGN KEY (account_no, customer_no) REFERENCES public."MB002003"(account_no, customer_no) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT chk_mb004005_dates CHECK (maturity_date IS NULL OR maturity_date > asof_date),
    CONSTRAINT fk_mb004005_created  FOREIGN KEY (created_by)  REFERENCES public."MB001001"(user_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_mb004005_modified FOREIGN KEY (modified_by) REFERENCES public."MB001001"(user_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_mb004005_verified FOREIGN KEY (verified_by) REFERENCES public."MB001001"(user_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT chk_mb004005_maker_checker CHECK (verified_by IS NULL OR verified_by <> created_by),
    CONSTRAINT chk_mb004005_verify_pair   CHECK ((verified_by IS NULL) = (verified_on IS NULL))
);

CREATE INDEX IF NOT EXISTS idx_mb004005_customer ON public."MB004005" (customer_no, created_on DESC);
CREATE INDEX IF NOT EXISTS idx_mb004005_branch_status ON public."MB004005" (branch_code, req_status);

-- MB004006  EMI Payment
CREATE TABLE IF NOT EXISTS public."MB004006" (
    id            BIGSERIAL PRIMARY KEY,
    branch_code   VARCHAR(20) NOT NULL,
    customer_no   VARCHAR(30) NOT NULL,
    account_no    VARCHAR(30) NOT NULL,
    account_status VARCHAR(20),   -- snapshot at request time
    account_type   VARCHAR(20),   -- snapshot at request time
    req_status   req_status_t NOT NULL DEFAULT 'PENDING',
    loan_account_no  VARCHAR(30) NOT NULL,
    loan_ac_status   VARCHAR(20),
    loan_ac_name     VARCHAR(150),
    emi_amount       amount_t NOT NULL,
    total_outstanding NUMERIC(18,2) CHECK (total_outstanding >= 0),
    int_outstanding   NUMERIC(18,2) CHECK (int_outstanding >= 0),
    expiry_date      DATE,
    instal_amount    NUMERIC(18,2),
    created_by   BIGINT       NOT NULL,
    created_on   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified_by  BIGINT,
    modified_on  TIMESTAMPTZ,
    verified_by  BIGINT,
    verified_on  TIMESTAMPTZ,
    CONSTRAINT fk_mb004006_branch FOREIGN KEY (branch_code) REFERENCES public."MB002002"(branch_code) ON UPDATE CASCADE ON DELETE RESTRICT,
    -- composite FK: the account must belong to THIS customer
    CONSTRAINT fk_mb004006_account FOREIGN KEY (account_no, customer_no) REFERENCES public."MB002003"(account_no, customer_no) ON UPDATE CASCADE ON DELETE RESTRICT,
    -- loan account must also belong to this customer
    CONSTRAINT fk_mb004006_loan FOREIGN KEY (loan_account_no, customer_no) REFERENCES public."MB002003"(account_no, customer_no) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_mb004006_created  FOREIGN KEY (created_by)  REFERENCES public."MB001001"(user_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_mb004006_modified FOREIGN KEY (modified_by) REFERENCES public."MB001001"(user_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_mb004006_verified FOREIGN KEY (verified_by) REFERENCES public."MB001001"(user_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT chk_mb004006_maker_checker CHECK (verified_by IS NULL OR verified_by <> created_by),
    CONSTRAINT chk_mb004006_verify_pair   CHECK ((verified_by IS NULL) = (verified_on IS NULL))
);

CREATE INDEX IF NOT EXISTS idx_mb004006_customer ON public."MB004006" (customer_no, created_on DESC);
CREATE INDEX IF NOT EXISTS idx_mb004006_branch_status ON public."MB004006" (branch_code, req_status);

-- MB004007  FD Closing
CREATE TABLE IF NOT EXISTS public."MB004007" (
    id            BIGSERIAL PRIMARY KEY,
    branch_code   VARCHAR(20) NOT NULL,
    customer_no   VARCHAR(30) NOT NULL,
    account_no    VARCHAR(30) NOT NULL,
    account_status VARCHAR(20),   -- snapshot at request time
    account_type   VARCHAR(20),   -- snapshot at request time
    req_status   req_status_t NOT NULL DEFAULT 'PENDING',
    fd_acid       VARCHAR(30) NOT NULL,
    cert_date     DATE,
    asof_date     DATE,
    deposit_amount NUMERIC(18,2),
    int_rate      NUMERIC(7,4),
    offset_rate   NUMERIC(7,4),
    tenure        INTEGER,
    maturity_value NUMERIC(18,2),
    maturity_date DATE,
    ben_name      VARCHAR(150),
    ben_account   VARCHAR(34),
    ben_bank      VARCHAR(150),
    ben_ifsc      ifsc_t,               -- duplicated in the Excel, kept once
    amount        amount_t,
    remarks       VARCHAR(255),
    created_by   BIGINT       NOT NULL,
    created_on   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified_by  BIGINT,
    modified_on  TIMESTAMPTZ,
    verified_by  BIGINT,
    verified_on  TIMESTAMPTZ,
    CONSTRAINT fk_mb004007_branch FOREIGN KEY (branch_code) REFERENCES public."MB002002"(branch_code) ON UPDATE CASCADE ON DELETE RESTRICT,
    -- composite FK: the account must belong to THIS customer
    CONSTRAINT fk_mb004007_account FOREIGN KEY (account_no, customer_no) REFERENCES public."MB002003"(account_no, customer_no) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_mb004007_fd FOREIGN KEY (fd_acid, customer_no) REFERENCES public."MB002003"(account_no, customer_no) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_mb004007_created  FOREIGN KEY (created_by)  REFERENCES public."MB001001"(user_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_mb004007_modified FOREIGN KEY (modified_by) REFERENCES public."MB001001"(user_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_mb004007_verified FOREIGN KEY (verified_by) REFERENCES public."MB001001"(user_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT chk_mb004007_maker_checker CHECK (verified_by IS NULL OR verified_by <> created_by),
    CONSTRAINT chk_mb004007_verify_pair   CHECK ((verified_by IS NULL) = (verified_on IS NULL))
);

CREATE INDEX IF NOT EXISTS idx_mb004007_customer ON public."MB004007" (customer_no, created_on DESC);
CREATE INDEX IF NOT EXISTS idx_mb004007_branch_status ON public."MB004007" (branch_code, req_status);

-- MB004008  Standing Instruction
CREATE TABLE IF NOT EXISTS public."MB004008" (
    id            BIGSERIAL PRIMARY KEY,
    branch_code   VARCHAR(20) NOT NULL,
    customer_no   VARCHAR(30) NOT NULL,
    account_no    VARCHAR(30) NOT NULL,
    account_status VARCHAR(20),   -- snapshot at request time
    account_type   VARCHAR(20),   -- snapshot at request time
    req_status   req_status_t NOT NULL DEFAULT 'PENDING',
    debit_amount  amount_t NOT NULL,
    tenure        INTEGER NOT NULL CHECK (tenure > 0),
    frequency     VARCHAR(20) NOT NULL,       -- MONTHLY / QUARTERLY ...
    expiry_date   DATE,
    cr_acid       VARCHAR(30) NOT NULL,       -- credit account (may be another customer's)
    cr_ac_status  VARCHAR(20),
    cr_ac_name    VARCHAR(150),
    created_by   BIGINT       NOT NULL,
    created_on   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified_by  BIGINT,
    modified_on  TIMESTAMPTZ,
    verified_by  BIGINT,
    verified_on  TIMESTAMPTZ,
    CONSTRAINT fk_mb004008_branch FOREIGN KEY (branch_code) REFERENCES public."MB002002"(branch_code) ON UPDATE CASCADE ON DELETE RESTRICT,
    -- composite FK: the account must belong to THIS customer
    CONSTRAINT fk_mb004008_account FOREIGN KEY (account_no, customer_no) REFERENCES public."MB002003"(account_no, customer_no) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_mb004008_created  FOREIGN KEY (created_by)  REFERENCES public."MB001001"(user_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_mb004008_modified FOREIGN KEY (modified_by) REFERENCES public."MB001001"(user_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_mb004008_verified FOREIGN KEY (verified_by) REFERENCES public."MB001001"(user_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT chk_mb004008_maker_checker CHECK (verified_by IS NULL OR verified_by <> created_by),
    CONSTRAINT chk_mb004008_verify_pair   CHECK ((verified_by IS NULL) = (verified_on IS NULL))
);

CREATE INDEX IF NOT EXISTS idx_mb004008_customer ON public."MB004008" (customer_no, created_on DESC);
CREATE INDEX IF NOT EXISTS idx_mb004008_branch_status ON public."MB004008" (branch_code, req_status);

-- MB004009  Locker Application
CREATE TABLE IF NOT EXISTS public."MB004009" (
    id            BIGSERIAL PRIMARY KEY,
    branch_code   VARCHAR(20) NOT NULL,
    customer_no   VARCHAR(30) NOT NULL,
    account_no    VARCHAR(30) NOT NULL,
    account_status VARCHAR(20),   -- snapshot at request time
    account_type   VARCHAR(20),   -- snapshot at request time
    req_status   req_status_t NOT NULL DEFAULT 'PENDING',
    mobile        VARCHAR(20) CHECK (mobile ~ '^[6-9][0-9]{9}$'),
    email         VARCHAR(254),
    operation_yrs SMALLINT CHECK (operation_yrs > 0),
    created_by   BIGINT       NOT NULL,
    created_on   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified_by  BIGINT,
    modified_on  TIMESTAMPTZ,
    verified_by  BIGINT,
    verified_on  TIMESTAMPTZ,
    CONSTRAINT fk_mb004009_branch FOREIGN KEY (branch_code) REFERENCES public."MB002002"(branch_code) ON UPDATE CASCADE ON DELETE RESTRICT,
    -- composite FK: the account must belong to THIS customer
    CONSTRAINT fk_mb004009_account FOREIGN KEY (account_no, customer_no) REFERENCES public."MB002003"(account_no, customer_no) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_mb004009_created  FOREIGN KEY (created_by)  REFERENCES public."MB001001"(user_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_mb004009_modified FOREIGN KEY (modified_by) REFERENCES public."MB001001"(user_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_mb004009_verified FOREIGN KEY (verified_by) REFERENCES public."MB001001"(user_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT chk_mb004009_maker_checker CHECK (verified_by IS NULL OR verified_by <> created_by),
    CONSTRAINT chk_mb004009_verify_pair   CHECK ((verified_by IS NULL) = (verified_on IS NULL))
);

CREATE INDEX IF NOT EXISTS idx_mb004009_customer ON public."MB004009" (customer_no, created_on DESC);
CREATE INDEX IF NOT EXISTS idx_mb004009_branch_status ON public."MB004009" (branch_code, req_status);

-- MB004010  Loan Application - no account_no in the Excel; links to the customer only
CREATE TABLE IF NOT EXISTS public."MB004010" (
    id            BIGSERIAL PRIMARY KEY,
    branch_code   VARCHAR(20) NOT NULL,
    customer_no   VARCHAR(30) NOT NULL,
    req_status   req_status_t NOT NULL DEFAULT 'PENDING',
    mobile        VARCHAR(20) CHECK (mobile ~ '^[6-9][0-9]{9}$'),
    email         VARCHAR(254),
    product_code  VARCHAR(20) NOT NULL,
    loan_amount   amount_t NOT NULL,
    total_income  NUMERIC(18,2) CHECK (total_income >= 0),
    total_exp     NUMERIC(18,2) CHECK (total_exp >= 0),
    eligibility   VARCHAR(30),
    created_by   BIGINT       NOT NULL,
    created_on   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified_by  BIGINT,
    modified_on  TIMESTAMPTZ,
    verified_by  BIGINT,
    verified_on  TIMESTAMPTZ,
    CONSTRAINT fk_mb004010_branch FOREIGN KEY (branch_code) REFERENCES public."MB002002"(branch_code) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_mb004010_customer FOREIGN KEY (customer_no) REFERENCES public.mb001001cr(customer_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_mb004010_created  FOREIGN KEY (created_by)  REFERENCES public."MB001001"(user_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_mb004010_modified FOREIGN KEY (modified_by) REFERENCES public."MB001001"(user_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_mb004010_verified FOREIGN KEY (verified_by) REFERENCES public."MB001001"(user_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT chk_mb004010_maker_checker CHECK (verified_by IS NULL OR verified_by <> created_by),
    CONSTRAINT chk_mb004010_verify_pair   CHECK ((verified_by IS NULL) = (verified_on IS NULL))
);

CREATE INDEX IF NOT EXISTS idx_mb004010_customer ON public."MB004010" (customer_no, created_on DESC);
CREATE INDEX IF NOT EXISTS idx_mb004010_branch_status ON public."MB004010" (branch_code, req_status);

-- ---------- 9. Auto-maintain modified_on --------------------------------
CREATE OR REPLACE FUNCTION public.fn_touch_modified_on() RETURNS trigger AS $$
BEGIN
  NEW.modified_on := CURRENT_TIMESTAMP;
  RETURN NEW;
END $$ LANGUAGE plpgsql;

DO $$
DECLARE t text;
BEGIN
  FOREACH t IN ARRAY ARRAY['MB001001','MB001002','MB001003','MB002001','MB002002','MB002003','MB003002',
      'MB004001','MB004002','MB004003','MB004004','MB004005','MB004006','MB004007','MB004008','MB004009','MB004010']
  LOOP
    EXECUTE format('DROP TRIGGER IF EXISTS trg_%1$s_touch ON public.%2$I', lower(t), t);
    EXECUTE format('CREATE TRIGGER trg_%1$s_touch BEFORE UPDATE ON public.%2$I FOR EACH ROW EXECUTE FUNCTION public.fn_touch_modified_on()', lower(t), t);
  END LOOP;
END $$;


-- ---------- 0. Reusable domains (change a rule in ONE place) ----------
DO $$ BEGIN
  CREATE DOMAIN req_status_t AS VARCHAR(20)
    CHECK (VALUE IN ('PENDING','APPROVED','REJECTED','CANCELLED','COMPLETED','FAILED'));
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
  CREATE DOMAIN ifsc_t AS VARCHAR(11) CHECK (VALUE ~ '^[A-Z]{4}0[A-Z0-9]{6}$');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
  CREATE DOMAIN amount_t AS NUMERIC(18,2) CHECK (VALUE > 0);
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- ---------- 1. Customer registry (from app: mb001001cr) --------
-- Seeded ONLY from core banking. Never from the client.
CREATE TABLE IF NOT EXISTS public.mb001001cr (
    customer_id                  VARCHAR(30) PRIMARY KEY,
    eligible_for_mobile_banking  BOOLEAN     NOT NULL DEFAULT TRUE,
    registered_mobile            VARCHAR(20) NOT NULL,
    CONSTRAINT chk_customer_mobile CHECK (registered_mobile ~ '^[6-9][0-9]{9}$')
);

-- ---------- 2. BASE TABLE MB001001  Usermaster (your DDL, renamed) ----
-- NOTE: your file created "Usermaster" but MB001002/3 point to "MB001001".
CREATE TABLE IF NOT EXISTS public."MB001001" (
    user_id BIGSERIAL PRIMARY KEY,

    customer_no VARCHAR(30) UNIQUE,
    emp_no VARCHAR(30) UNIQUE,

    title VARCHAR(10)
        CHECK (title IN ('Mr.', 'Mrs.', 'Miss', 'Ms.', 'Dr.')),

    user_type VARCHAR(10) NOT NULL
        CHECK (user_type IN ('CU', 'STAFF', 'ADMIN')),

    first_name VARCHAR(80) NOT NULL,
    middle_name VARCHAR(80),
    last_name VARCHAR(80),

    full_name VARCHAR(250)
        GENERATED ALWAYS AS (
            COALESCE(title || ' ', '') ||
            first_name ||
            COALESCE(' ' || NULLIF(middle_name, ''), '') ||
            COALESCE(' ' || NULLIF(last_name, ''), '')
        ) STORED,

    designation VARCHAR(100),
    dob DATE,
    email VARCHAR(254),
    mobile VARCHAR(20),

    user_name VARCHAR(80) NOT NULL UNIQUE,
    disp_name VARCHAR(100),

    user_role VARCHAR(50),
    sys_role VARCHAR(50),
    branch VARCHAR(100),

    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'ACTIVE', 'LOCKED', 'CLOSED')),

    multi_branch BOOLEAN NOT NULL DEFAULT FALSE,
    idle_timeout SMALLINT,

    bad_login SMALLINT NOT NULL DEFAULT 0
        CHECK (bad_login >= 0),

    login_time TIMESTAMPTZ,
    logout_time TIMESTAMPTZ,
    inop_date DATE,

    "2fa_login" BOOLEAN NOT NULL DEFAULT TRUE,
    authentication VARCHAR(50),

    created_by VARCHAR(80),
    created_on TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified_by VARCHAR(80),
    modified_on TIMESTAMPTZ,
    verified_by VARCHAR(80),
    verified_on TIMESTAMPTZ
);

-- Added links on the base table:
--  * a customer user must exist in the registry (staff keep customer_no NULL)
--  * customer users carry customer_no, staff/admin carry emp_no
DO $$ BEGIN
  ALTER TABLE public."MB001001"
    ADD CONSTRAINT fk_mb001001_customer
    FOREIGN KEY (customer_no) REFERENCES public.mb001001cr(customer_id)
    ON UPDATE CASCADE ON DELETE RESTRICT;
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
  ALTER TABLE public."MB001001"
    ADD CONSTRAINT chk_mb001001_identity CHECK (
      (user_type = 'CU' AND customer_no IS NOT NULL)
      OR (user_type <> 'CU' AND emp_no IS NOT NULL));
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- ---------- 3. BASE TABLE MB001002  Password (your DDL, unchanged) ----
CREATE TABLE IF NOT EXISTS public."MB001002" (
    password_id BIGSERIAL PRIMARY KEY,

    user_id BIGINT NOT NULL UNIQUE,

    password_hash VARCHAR(255) NOT NULL,
    password_changed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    must_reset BOOLEAN NOT NULL DEFAULT TRUE,

    created_by BIGINT,
    created_on TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified_by BIGINT,
    modified_on TIMESTAMPTZ,

    CONSTRAINT fk_mb001002_usermaster
        FOREIGN KEY (user_id)
        REFERENCES public."MB001001" (user_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT
);

-- ---------- 4. BASE TABLE MB001003  MPIN (your DDL, unchanged) --------
CREATE TABLE IF NOT EXISTS public."MB001003" (
    mpin_id BIGSERIAL PRIMARY KEY,

    -- Connects this MPIN record to Usermaster
    user_id BIGINT NOT NULL UNIQUE,

    -- Store only a secure hash of the MPIN
    mpin_hash VARCHAR(255) NOT NULL,

    pin_changed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    failed_attempts INTEGER NOT NULL DEFAULT 0,
    locked_until TIMESTAMPTZ,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

    created_by BIGINT,
    created_on TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified_by BIGINT,
    modified_on TIMESTAMPTZ,

    CONSTRAINT fk_mb001003_usermaster
        FOREIGN KEY (user_id)
        REFERENCES public."MB001001" (user_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT chk_mb001003_failed_attempts
        CHECK (failed_attempts >= 0),

    CONSTRAINT chk_mb001003_status
        CHECK (status IN ('ACTIVE', 'LOCKED', 'INACTIVE'))
);

-- ---------- 5. Registration flow (from app) ---------------------------
-- Enrolment record: which customer has a mobile-banking login.
-- The MPIN hash lives ONLY in MB001003 (single source of truth).
CREATE TABLE IF NOT EXISTS public.mobile_banking_users (
    customer_id         VARCHAR(30) PRIMARY KEY,
    user_id             BIGINT      NOT NULL UNIQUE,
    registration_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (registration_status IN ('ACTIVE','LOCKED','CLOSED')),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_mbu_customer FOREIGN KEY (customer_id)
        REFERENCES public.mb001001cr(customer_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_mbu_user FOREIGN KEY (user_id)
        REFERENCES public."MB001001"(user_id) ON UPDATE CASCADE ON DELETE RESTRICT
);

CREATE TABLE IF NOT EXISTS public.registration_challenges (
    id          BIGSERIAL PRIMARY KEY,
    customer_id VARCHAR(30) NOT NULL,
    otp_hash    VARCHAR(100) NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    attempts    INTEGER NOT NULL DEFAULT 0,
    consumed    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_registration_challenges_customer FOREIGN KEY (customer_id)
        REFERENCES public.mb001001cr(customer_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT chk_registration_challenge_attempts CHECK (attempts >= 0 AND attempts <= 5)
);
CREATE INDEX IF NOT EXISTS idx_reg_challenge_customer ON public.registration_challenges (customer_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_reg_challenge_expiry   ON public.registration_challenges (expires_at);
CREATE INDEX IF NOT EXISTS idx_reg_challenge_active   ON public.registration_challenges (customer_id, consumed, expires_at);

-- ---------- 6. Setup masters (MB002xxx) --------------------------------
-- MB002001 Bank Master  (Excel column "add" is a reserved word -> "address")
CREATE TABLE IF NOT EXISTS public."MB002001" (
    bank_code         VARCHAR(20) PRIMARY KEY,
    bank_name         VARCHAR(150) NOT NULL,
    bank_type         VARCHAR(30),
    rbi_license       VARCHAR(50),
    sponsor_bank      VARCHAR(20),
    ifsc_code         ifsc_t UNIQUE,
    account_no        VARCHAR(30),
    pan_no            VARCHAR(10) CHECK (pan_no ~ '^[A-Z]{5}[0-9]{4}[A-Z]$'),
    gst_no            VARCHAR(15) CHECK (gst_no ~ '^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$'),
    working_time_from TIME,
    working_time_to   TIME,
    micr_code         VARCHAR(9) CHECK (micr_code ~ '^[0-9]{9}$'),
    address           VARCHAR(150),
    add1              VARCHAR(150),
    add2              VARCHAR(150),
    add3              VARCHAR(150),
    city              VARCHAR(80),
    state             VARCHAR(80),
    pincode           VARCHAR(6) CHECK (pincode ~ '^[1-9][0-9]{5}$'),
    created_by  BIGINT,
    created_on  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified_by BIGINT,
    modified_on TIMESTAMPTZ,
    verified_by BIGINT,
    verified_on TIMESTAMPTZ,
    CONSTRAINT fk_mb002001_sponsor  FOREIGN KEY (sponsor_bank) REFERENCES public."MB002001"(bank_code) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_mb002001_created  FOREIGN KEY (created_by)  REFERENCES public."MB001001"(user_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_mb002001_modified FOREIGN KEY (modified_by) REFERENCES public."MB001001"(user_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_mb002001_verified FOREIGN KEY (verified_by) REFERENCES public."MB001001"(user_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT chk_mb002001_hours CHECK (working_time_from IS NULL OR working_time_to IS NULL OR working_time_from < working_time_to)
);

-- MB002002 Branch Master  (bank_code added so each branch belongs to a bank)
CREATE TABLE IF NOT EXISTS public."MB002002" (
    branch_code  VARCHAR(20) PRIMARY KEY,
    bank_code    VARCHAR(20) NOT NULL,
    branch_name  VARCHAR(150) NOT NULL,
    rbi_license  VARCHAR(50),
    micr_code    VARCHAR(9) CHECK (micr_code ~ '^[0-9]{9}$'),
    ifsc_code    ifsc_t NOT NULL UNIQUE,
    br_add       VARCHAR(150),
    br_add1      VARCHAR(150),
    br_add2      VARCHAR(150),
    br_add3      VARCHAR(150),
    br_city      VARCHAR(80),
    br_state     VARCHAR(80),
    br_pincode   VARCHAR(6) CHECK (br_pincode ~ '^[1-9][0-9]{5}$'),
    created_by  BIGINT,
    created_on  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified_by BIGINT,
    modified_on TIMESTAMPTZ,
    verified_by BIGINT,
    verified_on TIMESTAMPTZ,
    CONSTRAINT fk_mb002002_bank     FOREIGN KEY (bank_code)   REFERENCES public."MB002001"(bank_code) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_mb002002_created  FOREIGN KEY (created_by)  REFERENCES public."MB001001"(user_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_mb002002_modified FOREIGN KEY (modified_by) REFERENCES public."MB001001"(user_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_mb002002_verified FOREIGN KEY (verified_by) REFERENCES public."MB001001"(user_id) ON UPDATE CASCADE ON DELETE RESTRICT
);
CREATE INDEX IF NOT EXISTS idx_mb002002_bank ON public."MB002002"(bank_code);

-- MB002003 Account Master (NEW) - mirror of core-banking accounts.
-- Every request table points here, so a customer can only act on own accounts.
CREATE TABLE IF NOT EXISTS public."MB002003" (
    account_no     VARCHAR(30) PRIMARY KEY,
    customer_no    VARCHAR(30) NOT NULL,
    branch_code    VARCHAR(20) NOT NULL,
    account_type   VARCHAR(20) NOT NULL,   -- e.g. SAVINGS, CURRENT, FD, RD, LOAN
    account_status VARCHAR(20) NOT NULL,   -- e.g. ACTIVE, DORMANT, CLOSED
    created_on     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified_on    TIMESTAMPTZ,
    synced_on      TIMESTAMPTZ,
    CONSTRAINT uq_mb002003_account_customer UNIQUE (account_no, customer_no),
    CONSTRAINT fk_mb002003_customer FOREIGN KEY (customer_no) REFERENCES public.mb001001cr(customer_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_mb002003_branch   FOREIGN KEY (branch_code) REFERENCES public."MB002002"(branch_code) ON UPDATE CASCADE ON DELETE RESTRICT
);
CREATE INDEX IF NOT EXISTS idx_mb002003_customer ON public."MB002003"(customer_no);

-- ---------- 7. Notification & device (MB003xxx) ------------------------
CREATE TABLE IF NOT EXISTS public."MB003001" (
    id           BIGSERIAL PRIMARY KEY,
    branch_code  VARCHAR(20),
    customer_no  VARCHAR(30) NOT NULL,
    notification TEXT NOT NULL,
    is_read      BOOLEAN NOT NULL DEFAULT FALSE,           -- added
    created_on   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, -- added
    CONSTRAINT fk_mb003001_branch   FOREIGN KEY (branch_code) REFERENCES public."MB002002"(branch_code) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_mb003001_customer FOREIGN KEY (customer_no) REFERENCES public.mb001001cr(customer_id) ON UPDATE CASCADE ON DELETE RESTRICT
);
CREATE INDEX IF NOT EXISTS idx_mb003001_customer_unread ON public."MB003001"(customer_no, is_read, created_on DESC);

CREATE TABLE IF NOT EXISTS public."MB003002" (
    id          BIGSERIAL PRIMARY KEY,
    customer_no VARCHAR(30) NOT NULL,
    mobile      VARCHAR(20) NOT NULL CHECK (mobile ~ '^[6-9][0-9]{9}$'),
    email       VARCHAR(254),
    device_id   VARCHAR(100),                              -- added
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,             -- added
    created_by  BIGINT NOT NULL,
    created_on  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified_by BIGINT,
    modified_on TIMESTAMPTZ,
    verified_by BIGINT,
    verified_on TIMESTAMPTZ,
    CONSTRAINT fk_mb003002_customer FOREIGN KEY (customer_no) REFERENCES public.mb001001cr(customer_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_mb003002_created  FOREIGN KEY (created_by)  REFERENCES public."MB001001"(user_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_mb003002_modified FOREIGN KEY (modified_by) REFERENCES public."MB001001"(user_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_mb003002_verified FOREIGN KEY (verified_by) REFERENCES public."MB001001"(user_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT chk_mb003002_maker_checker CHECK (verified_by IS NULL OR verified_by <> created_by),
    CONSTRAINT chk_mb003002_verify_pair   CHECK ((verified_by IS NULL) = (verified_on IS NULL))
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_mb003002_active_device ON public."MB003002"(customer_no, device_id) WHERE is_active AND device_id IS NOT NULL;

-- ---------- 8. Customer service requests (MB004xxx) --------------------
-- Every table: branch -> MB002002, (account,customer) -> MB002003,
-- created/modified/verified_by -> MB001001, maker <> checker.

-- MB004001  Remittance
CREATE TABLE IF NOT EXISTS public."MB004001" (
    id            BIGSERIAL PRIMARY KEY,
    branch_code   VARCHAR(20) NOT NULL,
    customer_no   VARCHAR(30) NOT NULL,
    account_no    VARCHAR(30) NOT NULL,
    account_status VARCHAR(20),   -- snapshot at request time
    account_type   VARCHAR(20),   -- snapshot at request time
    req_status   req_status_t NOT NULL DEFAULT 'PENDING',
    ifsc_code     ifsc_t,
    pay_mode      VARCHAR(20) NOT NULL,       -- NEFT / RTGS / IMPS ...
    ben_name      VARCHAR(150) NOT NULL,
    ben_account   VARCHAR(34) NOT NULL,
    ben_bank      VARCHAR(150),
    ben_ifsc      ifsc_t NOT NULL,
    amount        amount_t NOT NULL,
    remarks       VARCHAR(255),
    created_by   BIGINT       NOT NULL,
    created_on   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified_by  BIGINT,
    modified_on  TIMESTAMPTZ,
    verified_by  BIGINT,
    verified_on  TIMESTAMPTZ,
    CONSTRAINT fk_mb004001_branch FOREIGN KEY (branch_code) REFERENCES public."MB002002"(branch_code) ON UPDATE CASCADE ON DELETE RESTRICT,
    -- composite FK: the account must belong to THIS customer
    CONSTRAINT fk_mb004001_account FOREIGN KEY (account_no, customer_no) REFERENCES public."MB002003"(account_no, customer_no) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_mb004001_created  FOREIGN KEY (created_by)  REFERENCES public."MB001001"(user_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_mb004001_modified FOREIGN KEY (modified_by) REFERENCES public."MB001001"(user_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_mb004001_verified FOREIGN KEY (verified_by) REFERENCES public."MB001001"(user_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT chk_mb004001_maker_checker CHECK (verified_by IS NULL OR verified_by <> created_by),
    CONSTRAINT chk_mb004001_verify_pair   CHECK ((verified_by IS NULL) = (verified_on IS NULL))
);

CREATE INDEX IF NOT EXISTS idx_mb004001_customer ON public."MB004001" (customer_no, created_on DESC);
CREATE INDEX IF NOT EXISTS idx_mb004001_branch_status ON public."MB004001" (branch_code, req_status);

-- MB004002  Cheque Book
CREATE TABLE IF NOT EXISTS public."MB004002" (
    id            BIGSERIAL PRIMARY KEY,
    branch_code   VARCHAR(20) NOT NULL,
    customer_no   VARCHAR(30) NOT NULL,
    account_no    VARCHAR(30) NOT NULL,
    account_status VARCHAR(20),   -- snapshot at request time
    account_type   VARCHAR(20),   -- snapshot at request time
    req_status   req_status_t NOT NULL DEFAULT 'PENDING',
    noof_books    SMALLINT NOT NULL CHECK (noof_books > 0),
    delivery      VARCHAR(20) NOT NULL        -- BRANCH / HOME ...,
    created_by   BIGINT       NOT NULL,
    created_on   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified_by  BIGINT,
    modified_on  TIMESTAMPTZ,
    verified_by  BIGINT,
    verified_on  TIMESTAMPTZ,
    CONSTRAINT fk_mb004002_branch FOREIGN KEY (branch_code) REFERENCES public."MB002002"(branch_code) ON UPDATE CASCADE ON DELETE RESTRICT,
    -- composite FK: the account must belong to THIS customer
    CONSTRAINT fk_mb004002_account FOREIGN KEY (account_no, customer_no) REFERENCES public."MB002003"(account_no, customer_no) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_mb004002_created  FOREIGN KEY (created_by)  REFERENCES public."MB001001"(user_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_mb004002_modified FOREIGN KEY (modified_by) REFERENCES public."MB001001"(user_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_mb004002_verified FOREIGN KEY (verified_by) REFERENCES public."MB001001"(user_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT chk_mb004002_maker_checker CHECK (verified_by IS NULL OR verified_by <> created_by),
    CONSTRAINT chk_mb004002_verify_pair   CHECK ((verified_by IS NULL) = (verified_on IS NULL))
);

CREATE INDEX IF NOT EXISTS idx_mb004002_customer ON public."MB004002" (customer_no, created_on DESC);
CREATE INDEX IF NOT EXISTS idx_mb004002_branch_status ON public."MB004002" (branch_code, req_status);

-- MB004003  Stop Payment
CREATE TABLE IF NOT EXISTS public."MB004003" (
    id            BIGSERIAL PRIMARY KEY,
    branch_code   VARCHAR(20) NOT NULL,
    customer_no   VARCHAR(30) NOT NULL,
    account_no    VARCHAR(30) NOT NULL,
    account_status VARCHAR(20),   -- snapshot at request time
    account_type   VARCHAR(20),   -- snapshot at request time
    req_status   req_status_t NOT NULL DEFAULT 'PENDING',
    cheque_no     VARCHAR(20) NOT NULL,
    cheque_date   DATE,
    issued_to     VARCHAR(150),
    cheque_amount amount_t,
    reason        VARCHAR(255),
    created_by   BIGINT       NOT NULL,
    created_on   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified_by  BIGINT,
    modified_on  TIMESTAMPTZ,
    verified_by  BIGINT,
    verified_on  TIMESTAMPTZ,
    CONSTRAINT fk_mb004003_branch FOREIGN KEY (branch_code) REFERENCES public."MB002002"(branch_code) ON UPDATE CASCADE ON DELETE RESTRICT,
    -- composite FK: the account must belong to THIS customer
    CONSTRAINT fk_mb004003_account FOREIGN KEY (account_no, customer_no) REFERENCES public."MB002003"(account_no, customer_no) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_mb004003_created  FOREIGN KEY (created_by)  REFERENCES public."MB001001"(user_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_mb004003_modified FOREIGN KEY (modified_by) REFERENCES public."MB001001"(user_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_mb004003_verified FOREIGN KEY (verified_by) REFERENCES public."MB001001"(user_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT chk_mb004003_maker_checker CHECK (verified_by IS NULL OR verified_by <> created_by),
    CONSTRAINT chk_mb004003_verify_pair   CHECK ((verified_by IS NULL) = (verified_on IS NULL))
);

CREATE INDEX IF NOT EXISTS idx_mb004003_customer ON public."MB004003" (customer_no, created_on DESC);
CREATE INDEX IF NOT EXISTS idx_mb004003_branch_status ON public."MB004003" (branch_code, req_status);
-- one live stop-payment per cheque
CREATE UNIQUE INDEX IF NOT EXISTS uq_mb004003_live_cheque ON public."MB004003"(account_no, cheque_no)
    WHERE req_status IN ('PENDING','APPROVED','COMPLETED');

-- MB004004  FD Opening
CREATE TABLE IF NOT EXISTS public."MB004004" (
    id            BIGSERIAL PRIMARY KEY,
    branch_code   VARCHAR(20) NOT NULL,
    customer_no   VARCHAR(30) NOT NULL,
    account_no    VARCHAR(30) NOT NULL,
    debit_account_status VARCHAR(20),   -- snapshot at request time
    debit_account_type   VARCHAR(20),   -- snapshot at request time
    req_status   req_status_t NOT NULL DEFAULT 'PENDING',
    prod_code     VARCHAR(20) NOT NULL,
    prod_name     VARCHAR(100),
    scheme_code   VARCHAR(20) NOT NULL,
    scheme_name   VARCHAR(100),
    cert_date     DATE,
    asof_date     DATE NOT NULL,
    deposit_amount amount_t NOT NULL,
    int_rate      NUMERIC(7,4) NOT NULL CHECK (int_rate >= 0),
    offset_rate   NUMERIC(7,4),
    tenure        INTEGER NOT NULL CHECK (tenure > 0),   -- months
    auto_renewal  BOOLEAN NOT NULL DEFAULT FALSE,
    maturity_date DATE,
    maturity_value NUMERIC(18,2),
    cr_int_acid   VARCHAR(30),
    maturity_acid VARCHAR(30),
    renew_int     BOOLEAN NOT NULL DEFAULT FALSE,
    created_by   BIGINT       NOT NULL,
    created_on   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified_by  BIGINT,
    modified_on  TIMESTAMPTZ,
    verified_by  BIGINT,
    verified_on  TIMESTAMPTZ,
    CONSTRAINT fk_mb004004_branch FOREIGN KEY (branch_code) REFERENCES public."MB002002"(branch_code) ON UPDATE CASCADE ON DELETE RESTRICT,
    -- composite FK: the account must belong to THIS customer
    CONSTRAINT fk_mb004004_account FOREIGN KEY (account_no, customer_no) REFERENCES public."MB002003"(account_no, customer_no) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT chk_mb004004_dates CHECK (maturity_date IS NULL OR maturity_date > asof_date),
    CONSTRAINT fk_mb004004_created  FOREIGN KEY (created_by)  REFERENCES public."MB001001"(user_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_mb004004_modified FOREIGN KEY (modified_by) REFERENCES public."MB001001"(user_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_mb004004_verified FOREIGN KEY (verified_by) REFERENCES public."MB001001"(user_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT chk_mb004004_maker_checker CHECK (verified_by IS NULL OR verified_by <> created_by),
    CONSTRAINT chk_mb004004_verify_pair   CHECK ((verified_by IS NULL) = (verified_on IS NULL))
);

CREATE INDEX IF NOT EXISTS idx_mb004004_customer ON public."MB004004" (customer_no, created_on DESC);
CREATE INDEX IF NOT EXISTS idx_mb004004_branch_status ON public."MB004004" (branch_code, req_status);

-- MB004005  RD Opening - deposit_amount = monthly instalment
CREATE TABLE IF NOT EXISTS public."MB004005" (
    id            BIGSERIAL PRIMARY KEY,
    branch_code   VARCHAR(20) NOT NULL,
    customer_no   VARCHAR(30) NOT NULL,
    account_no    VARCHAR(30) NOT NULL,
    debit_account_status VARCHAR(20),   -- snapshot at request time
    debit_account_type   VARCHAR(20),   -- snapshot at request time
    req_status   req_status_t NOT NULL DEFAULT 'PENDING',
    prod_code     VARCHAR(20) NOT NULL,
    prod_name     VARCHAR(100),
    scheme_code   VARCHAR(20) NOT NULL,
    scheme_name   VARCHAR(100),
    cert_date     DATE,
    asof_date     DATE NOT NULL,
    deposit_amount amount_t NOT NULL,
    int_rate      NUMERIC(7,4) NOT NULL CHECK (int_rate >= 0),
    offset_rate   NUMERIC(7,4),
    tenure        INTEGER NOT NULL CHECK (tenure > 0),   -- months
    auto_renewal  BOOLEAN NOT NULL DEFAULT FALSE,
    maturity_date DATE,
    maturity_value NUMERIC(18,2),
    cr_int_acid   VARCHAR(30),
    maturity_acid VARCHAR(30),
    created_by   BIGINT       NOT NULL,
    created_on   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified_by  BIGINT,
    modified_on  TIMESTAMPTZ,
    verified_by  BIGINT,
    verified_on  TIMESTAMPTZ,
    CONSTRAINT fk_mb004005_branch FOREIGN KEY (branch_code) REFERENCES public."MB002002"(branch_code) ON UPDATE CASCADE ON DELETE RESTRICT,
    -- composite FK: the account must belong to THIS customer
    CONSTRAINT fk_mb004005_account FOREIGN KEY (account_no, customer_no) REFERENCES public."MB002003"(account_no, customer_no) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT chk_mb004005_dates CHECK (maturity_date IS NULL OR maturity_date > asof_date),
    CONSTRAINT fk_mb004005_created  FOREIGN KEY (created_by)  REFERENCES public."MB001001"(user_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_mb004005_modified FOREIGN KEY (modified_by) REFERENCES public."MB001001"(user_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_mb004005_verified FOREIGN KEY (verified_by) REFERENCES public."MB001001"(user_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT chk_mb004005_maker_checker CHECK (verified_by IS NULL OR verified_by <> created_by),
    CONSTRAINT chk_mb004005_verify_pair   CHECK ((verified_by IS NULL) = (verified_on IS NULL))
);

CREATE INDEX IF NOT EXISTS idx_mb004005_customer ON public."MB004005" (customer_no, created_on DESC);
CREATE INDEX IF NOT EXISTS idx_mb004005_branch_status ON public."MB004005" (branch_code, req_status);

-- MB004006  EMI Payment
CREATE TABLE IF NOT EXISTS public."MB004006" (
    id            BIGSERIAL PRIMARY KEY,
    branch_code   VARCHAR(20) NOT NULL,
    customer_no   VARCHAR(30) NOT NULL,
    account_no    VARCHAR(30) NOT NULL,
    account_status VARCHAR(20),   -- snapshot at request time
    account_type   VARCHAR(20),   -- snapshot at request time
    req_status   req_status_t NOT NULL DEFAULT 'PENDING',
    loan_account_no  VARCHAR(30) NOT NULL,
    loan_ac_status   VARCHAR(20),
    loan_ac_name     VARCHAR(150),
    emi_amount       amount_t NOT NULL,
    total_outstanding NUMERIC(18,2) CHECK (total_outstanding >= 0),
    int_outstanding   NUMERIC(18,2) CHECK (int_outstanding >= 0),
    expiry_date      DATE,
    instal_amount    NUMERIC(18,2),
    created_by   BIGINT       NOT NULL,
    created_on   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified_by  BIGINT,
    modified_on  TIMESTAMPTZ,
    verified_by  BIGINT,
    verified_on  TIMESTAMPTZ,
    CONSTRAINT fk_mb004006_branch FOREIGN KEY (branch_code) REFERENCES public."MB002002"(branch_code) ON UPDATE CASCADE ON DELETE RESTRICT,
    -- composite FK: the account must belong to THIS customer
    CONSTRAINT fk_mb004006_account FOREIGN KEY (account_no, customer_no) REFERENCES public."MB002003"(account_no, customer_no) ON UPDATE CASCADE ON DELETE RESTRICT,
    -- loan account must also belong to this customer
    CONSTRAINT fk_mb004006_loan FOREIGN KEY (loan_account_no, customer_no) REFERENCES public."MB002003"(account_no, customer_no) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_mb004006_created  FOREIGN KEY (created_by)  REFERENCES public."MB001001"(user_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_mb004006_modified FOREIGN KEY (modified_by) REFERENCES public."MB001001"(user_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_mb004006_verified FOREIGN KEY (verified_by) REFERENCES public."MB001001"(user_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT chk_mb004006_maker_checker CHECK (verified_by IS NULL OR verified_by <> created_by),
    CONSTRAINT chk_mb004006_verify_pair   CHECK ((verified_by IS NULL) = (verified_on IS NULL))
);

CREATE INDEX IF NOT EXISTS idx_mb004006_customer ON public."MB004006" (customer_no, created_on DESC);
CREATE INDEX IF NOT EXISTS idx_mb004006_branch_status ON public."MB004006" (branch_code, req_status);

-- MB004007  FD Closing
CREATE TABLE IF NOT EXISTS public."MB004007" (
    id            BIGSERIAL PRIMARY KEY,
    branch_code   VARCHAR(20) NOT NULL,
    customer_no   VARCHAR(30) NOT NULL,
    account_no    VARCHAR(30) NOT NULL,
    account_status VARCHAR(20),   -- snapshot at request time
    account_type   VARCHAR(20),   -- snapshot at request time
    req_status   req_status_t NOT NULL DEFAULT 'PENDING',
    fd_acid       VARCHAR(30) NOT NULL,
    cert_date     DATE,
    asof_date     DATE,
    deposit_amount NUMERIC(18,2),
    int_rate      NUMERIC(7,4),
    offset_rate   NUMERIC(7,4),
    tenure        INTEGER,
    maturity_value NUMERIC(18,2),
    maturity_date DATE,
    ben_name      VARCHAR(150),
    ben_account   VARCHAR(34),
    ben_bank      VARCHAR(150),
    ben_ifsc      ifsc_t,               -- duplicated in the Excel, kept once
    amount        amount_t,
    remarks       VARCHAR(255),
    created_by   BIGINT       NOT NULL,
    created_on   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified_by  BIGINT,
    modified_on  TIMESTAMPTZ,
    verified_by  BIGINT,
    verified_on  TIMESTAMPTZ,
    CONSTRAINT fk_mb004007_branch FOREIGN KEY (branch_code) REFERENCES public."MB002002"(branch_code) ON UPDATE CASCADE ON DELETE RESTRICT,
    -- composite FK: the account must belong to THIS customer
    CONSTRAINT fk_mb004007_account FOREIGN KEY (account_no, customer_no) REFERENCES public."MB002003"(account_no, customer_no) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_mb004007_fd FOREIGN KEY (fd_acid, customer_no) REFERENCES public."MB002003"(account_no, customer_no) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_mb004007_created  FOREIGN KEY (created_by)  REFERENCES public."MB001001"(user_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_mb004007_modified FOREIGN KEY (modified_by) REFERENCES public."MB001001"(user_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_mb004007_verified FOREIGN KEY (verified_by) REFERENCES public."MB001001"(user_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT chk_mb004007_maker_checker CHECK (verified_by IS NULL OR verified_by <> created_by),
    CONSTRAINT chk_mb004007_verify_pair   CHECK ((verified_by IS NULL) = (verified_on IS NULL))
);

CREATE INDEX IF NOT EXISTS idx_mb004007_customer ON public."MB004007" (customer_no, created_on DESC);
CREATE INDEX IF NOT EXISTS idx_mb004007_branch_status ON public."MB004007" (branch_code, req_status);

-- MB004008  Standing Instruction
CREATE TABLE IF NOT EXISTS public."MB004008" (
    id            BIGSERIAL PRIMARY KEY,
    branch_code   VARCHAR(20) NOT NULL,
    customer_no   VARCHAR(30) NOT NULL,
    account_no    VARCHAR(30) NOT NULL,
    account_status VARCHAR(20),   -- snapshot at request time
    account_type   VARCHAR(20),   -- snapshot at request time
    req_status   req_status_t NOT NULL DEFAULT 'PENDING',
    debit_amount  amount_t NOT NULL,
    tenure        INTEGER NOT NULL CHECK (tenure > 0),
    frequency     VARCHAR(20) NOT NULL,       -- MONTHLY / QUARTERLY ...
    expiry_date   DATE,
    cr_acid       VARCHAR(30) NOT NULL,       -- credit account (may be another customer's)
    cr_ac_status  VARCHAR(20),
    cr_ac_name    VARCHAR(150),
    created_by   BIGINT       NOT NULL,
    created_on   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified_by  BIGINT,
    modified_on  TIMESTAMPTZ,
    verified_by  BIGINT,
    verified_on  TIMESTAMPTZ,
    CONSTRAINT fk_mb004008_branch FOREIGN KEY (branch_code) REFERENCES public."MB002002"(branch_code) ON UPDATE CASCADE ON DELETE RESTRICT,
    -- composite FK: the account must belong to THIS customer
    CONSTRAINT fk_mb004008_account FOREIGN KEY (account_no, customer_no) REFERENCES public."MB002003"(account_no, customer_no) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_mb004008_created  FOREIGN KEY (created_by)  REFERENCES public."MB001001"(user_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_mb004008_modified FOREIGN KEY (modified_by) REFERENCES public."MB001001"(user_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_mb004008_verified FOREIGN KEY (verified_by) REFERENCES public."MB001001"(user_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT chk_mb004008_maker_checker CHECK (verified_by IS NULL OR verified_by <> created_by),
    CONSTRAINT chk_mb004008_verify_pair   CHECK ((verified_by IS NULL) = (verified_on IS NULL))
);

CREATE INDEX IF NOT EXISTS idx_mb004008_customer ON public."MB004008" (customer_no, created_on DESC);
CREATE INDEX IF NOT EXISTS idx_mb004008_branch_status ON public."MB004008" (branch_code, req_status);

-- MB004009  Locker Application
CREATE TABLE IF NOT EXISTS public."MB004009" (
    id            BIGSERIAL PRIMARY KEY,
    branch_code   VARCHAR(20) NOT NULL,
    customer_no   VARCHAR(30) NOT NULL,
    account_no    VARCHAR(30) NOT NULL,
    account_status VARCHAR(20),   -- snapshot at request time
    account_type   VARCHAR(20),   -- snapshot at request time
    req_status   req_status_t NOT NULL DEFAULT 'PENDING',
    mobile        VARCHAR(20) CHECK (mobile ~ '^[6-9][0-9]{9}$'),
    email         VARCHAR(254),
    operation_yrs SMALLINT CHECK (operation_yrs > 0),
    created_by   BIGINT       NOT NULL,
    created_on   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified_by  BIGINT,
    modified_on  TIMESTAMPTZ,
    verified_by  BIGINT,
    verified_on  TIMESTAMPTZ,
    CONSTRAINT fk_mb004009_branch FOREIGN KEY (branch_code) REFERENCES public."MB002002"(branch_code) ON UPDATE CASCADE ON DELETE RESTRICT,
    -- composite FK: the account must belong to THIS customer
    CONSTRAINT fk_mb004009_account FOREIGN KEY (account_no, customer_no) REFERENCES public."MB002003"(account_no, customer_no) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_mb004009_created  FOREIGN KEY (created_by)  REFERENCES public."MB001001"(user_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_mb004009_modified FOREIGN KEY (modified_by) REFERENCES public."MB001001"(user_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_mb004009_verified FOREIGN KEY (verified_by) REFERENCES public."MB001001"(user_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT chk_mb004009_maker_checker CHECK (verified_by IS NULL OR verified_by <> created_by),
    CONSTRAINT chk_mb004009_verify_pair   CHECK ((verified_by IS NULL) = (verified_on IS NULL))
);

CREATE INDEX IF NOT EXISTS idx_mb004009_customer ON public."MB004009" (customer_no, created_on DESC);
CREATE INDEX IF NOT EXISTS idx_mb004009_branch_status ON public."MB004009" (branch_code, req_status);

-- MB004010  Loan Application - no account_no in the Excel; links to the customer only
CREATE TABLE IF NOT EXISTS public."MB004010" (
    id            BIGSERIAL PRIMARY KEY,
    branch_code   VARCHAR(20) NOT NULL,
    customer_no   VARCHAR(30) NOT NULL,
    req_status   req_status_t NOT NULL DEFAULT 'PENDING',
    mobile        VARCHAR(20) CHECK (mobile ~ '^[6-9][0-9]{9}$'),
    email         VARCHAR(254),
    product_code  VARCHAR(20) NOT NULL,
    loan_amount   amount_t NOT NULL,
    total_income  NUMERIC(18,2) CHECK (total_income >= 0),
    total_exp     NUMERIC(18,2) CHECK (total_exp >= 0),
    eligibility   VARCHAR(30),
    created_by   BIGINT       NOT NULL,
    created_on   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified_by  BIGINT,
    modified_on  TIMESTAMPTZ,
    verified_by  BIGINT,
    verified_on  TIMESTAMPTZ,
    CONSTRAINT fk_mb004010_branch FOREIGN KEY (branch_code) REFERENCES public."MB002002"(branch_code) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_mb004010_customer FOREIGN KEY (customer_no) REFERENCES public.mb001001cr(customer_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_mb004010_created  FOREIGN KEY (created_by)  REFERENCES public."MB001001"(user_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_mb004010_modified FOREIGN KEY (modified_by) REFERENCES public."MB001001"(user_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_mb004010_verified FOREIGN KEY (verified_by) REFERENCES public."MB001001"(user_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT chk_mb004010_maker_checker CHECK (verified_by IS NULL OR verified_by <> created_by),
    CONSTRAINT chk_mb004010_verify_pair   CHECK ((verified_by IS NULL) = (verified_on IS NULL))
);

CREATE INDEX IF NOT EXISTS idx_mb004010_customer ON public."MB004010" (customer_no, created_on DESC);
CREATE INDEX IF NOT EXISTS idx_mb004010_branch_status ON public."MB004010" (branch_code, req_status);

-- ---------- 9. Auto-maintain modified_on --------------------------------
CREATE OR REPLACE FUNCTION public.fn_touch_modified_on() RETURNS trigger AS $$
BEGIN
  NEW.modified_on := CURRENT_TIMESTAMP;
  RETURN NEW;
END $$ LANGUAGE plpgsql;

DO $$
DECLARE t text;
BEGIN
  FOREACH t IN ARRAY ARRAY['MB001001','MB001002','MB001003','MB002001','MB002002','MB002003','MB003002',
      'MB004001','MB004002','MB004003','MB004004','MB004005','MB004006','MB004007','MB004008','MB004009','MB004010']
  LOOP
    EXECUTE format('DROP TRIGGER IF EXISTS trg_%1$s_touch ON public.%2$I', lower(t), t);
    EXECUTE format('CREATE TRIGGER trg_%1$s_touch BEFORE UPDATE ON public.%2$I FOR EACH ROW EXECUTE FUNCTION public.fn_touch_modified_on()', lower(t), t);
  END LOOP;
END $$;

Table: public.registration_challenges
-- Purpose: Store OTP verification challenges for customer registration.

CREATE TABLE IF NOT EXISTS public.registration_challenges (
    id BIGSERIAL PRIMARY KEY,

    customer_id VARCHAR(30) NOT NULL,

    otp_hash VARCHAR(100) NOT NULL,

    expires_at TIMESTAMPTZ NOT NULL,

    attempts INTEGER NOT NULL DEFAULT 0,

    consumed BOOLEAN NOT NULL DEFAULT FALSE,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_registration_challenges_customer
        FOREIGN KEY (customer_id)
        REFERENCES public.mb001001cr(customer_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT chk_registration_challenge_attempts
        CHECK (attempts >= 0 AND attempts <= 5)
);

-- Supports retrieving a customer's challenges, newest first.
CREATE INDEX IF NOT EXISTS idx_reg_challenge_customer
    ON public.registration_challenges
    (customer_id, created_at DESC);

-- Supports finding expired challenges for cleanup.
CREATE INDEX IF NOT EXISTS idx_reg_challenge_expiry
    ON public.registration_challenges (expires_at);

-- Supports finding unused challenges during verification.
CREATE INDEX IF NOT EXISTS idx_reg_challenge_active
    ON public.registration_challenges (customer_id, consumed, expires_at);


-- Confirm with your bank's existing customer master before applying.
-- Seed mb001001cr from a trusted core-banking source; do NOT accept client-created customer/mobile pairs.
CREATE TABLE IF NOT EXISTS mb001001cr(
    customer_id VARCHAR(30) PRIMARY KEY,
    registered_mobile VARCHAR(20) NOT NULL,
    eligible_for_mobile_banking BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT chk_customer_mobile CHECK (registered_mobile ~ '^[6-9][0-9]{9}$')
);

CREATE TABLE IF NOT EXISTS registration_challenges (
    id BIGSERIAL PRIMARY KEY,
    customer_id VARCHAR(30) NOT NULL REFERENCES mb001001cr(customer_id),
    otp_hash VARCHAR(100) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0 AND attempts <= 5),
    consumed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_reg_challenge_customer 
ON registration_challenges(customer_id, created_at DESC);
