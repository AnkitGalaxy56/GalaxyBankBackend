
-- ==========================================
-- 1. NOMINEE DETAILS
-- Table: MB004014
-- ==========================================

CREATE TABLE IF NOT EXISTS "MB004014" (
    id BIGSERIAL PRIMARY KEY,
    branch_code VARCHAR(20) NOT NULL,
    customer_no VARCHAR(30) NOT NULL,
    account_no VARCHAR(30) NOT NULL,

    nominee_name VARCHAR(150) NOT NULL,
    relationship VARCHAR(50) NOT NULL,
    date_of_birth DATE,
    mobile_no VARCHAR(15),
    address TEXT,

    allocation_percentage NUMERIC(5,2) DEFAULT 100.00,
    is_minor BOOLEAN DEFAULT FALSE,
    guardian_name VARCHAR(150),

    req_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',

    created_by BIGINT NOT NULL,
    created_on TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified_by BIGINT,
    modified_on TIMESTAMPTZ,
    verified_by BIGINT,
    verified_on TIMESTAMPTZ,

    CONSTRAINT fk_mb004014_branch
        FOREIGN KEY (branch_code)
        REFERENCES "MB002002"(branch_code),

    CONSTRAINT fk_mb004014_customer_account
        FOREIGN KEY (account_no, customer_no)
        REFERENCES "MB002003"(account_no, customer_no),

    CONSTRAINT fk_mb004014_created_by
        FOREIGN KEY (created_by)
        REFERENCES "MB001001"(user_id),

    CONSTRAINT fk_mb004014_modified_by
        FOREIGN KEY (modified_by)
        REFERENCES "MB001001"(user_id),

    CONSTRAINT fk_mb004014_verified_by
        FOREIGN KEY (verified_by)
        REFERENCES "MB001001"(user_id),

    CONSTRAINT chk_mb004014_allocation
        CHECK (allocation_percentage > 0 AND allocation_percentage <= 100)
);


-- ==========================================
-- 2. KYC DOCUMENT DETAILS
-- Table: MB004015
-- ==========================================

CREATE TABLE IF NOT EXISTS "MB004015" (
    id BIGSERIAL PRIMARY KEY,
    customer_no VARCHAR(30) NOT NULL,
    account_no VARCHAR(30) NOT NULL,

    document_type VARCHAR(50) NOT NULL,
    document_name VARCHAR(150) NOT NULL,
    original_file_name VARCHAR(255),
    stored_file_name VARCHAR(255) NOT NULL,
    file_path TEXT NOT NULL,
    file_extension VARCHAR(10) NOT NULL,
    file_size BIGINT,

    upload_status VARCHAR(20) NOT NULL DEFAULT 'UPLOADED',

    created_by BIGINT NOT NULL,
    created_on TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified_on TIMESTAMPTZ,

    CONSTRAINT fk_mb004015_customer_account
        FOREIGN KEY (account_no, customer_no)
        REFERENCES "MB002003"(account_no, customer_no),

    CONSTRAINT fk_mb004015_created_by
        FOREIGN KEY (created_by)
        REFERENCES "MB001001"(user_id),

    CONSTRAINT chk_mb004015_extension
        CHECK (LOWER(file_extension) IN ('jpg', 'jpeg', 'png')),

    CONSTRAINT chk_mb004015_file_size
        CHECK (file_size IS NULL OR file_size > 0)
);


-- ==========================================
-- 3. KYC VERIFICATION & PROGRESS
-- Table: MB004016
-- ==========================================

CREATE TABLE IF NOT EXISTS "MB004016" (
    id BIGSERIAL PRIMARY KEY,
    customer_no VARCHAR(30) NOT NULL,
    account_no VARCHAR(30) NOT NULL,

    current_step SMALLINT NOT NULL DEFAULT 1,

    upload_documents BOOLEAN NOT NULL DEFAULT FALSE,
    verify_details BOOLEAN NOT NULL DEFAULT FALSE,
    review_completed BOOLEAN NOT NULL DEFAULT FALSE,
    final_approved BOOLEAN NOT NULL DEFAULT FALSE,

    kyc_status VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
    rejection_reason TEXT,

    submitted_on TIMESTAMPTZ,
    verified_by BIGINT,
    verified_on TIMESTAMPTZ,

    created_on TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified_on TIMESTAMPTZ,

    CONSTRAINT fk_mb004016_customer_account
        FOREIGN KEY (account_no, customer_no)
        REFERENCES "MB002003"(account_no, customer_no),

    CONSTRAINT fk_mb004016_verified_by
        FOREIGN KEY (verified_by)
        REFERENCES "MB001001"(user_id),

    CONSTRAINT uq_mb004016_customer_account
        UNIQUE (customer_no, account_no),

    CONSTRAINT chk_mb004016_step
        CHECK (current_step BETWEEN 1 AND 4)
);


-- ==========================================
-- INDEXES
-- ==========================================

CREATE INDEX idx_mb004014_customer
ON "MB004014"(customer_no, account_no);

CREATE INDEX idx_mb004015_customer
ON "MB004015"(customer_no, account_no);

CREATE INDEX idx_mb004016_status
ON "MB004016"(kyc_status);
