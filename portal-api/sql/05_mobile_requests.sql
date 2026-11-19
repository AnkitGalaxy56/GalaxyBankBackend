-- Mobile Banking request modules: Nomination, KYC and Positive Pay.
-- Apply on the portal PostgreSQL database after verifying referenced master tables.

CREATE TABLE IF NOT EXISTS public."MB004014" (
 id BIGSERIAL PRIMARY KEY, branch_code VARCHAR(20) NOT NULL, customer_no VARCHAR(30) NOT NULL, account_no VARCHAR(30) NOT NULL,
 nominee_name VARCHAR(150) NOT NULL, relationship VARCHAR(50) NOT NULL, date_of_birth DATE, mobile_no VARCHAR(15), address TEXT,
 allocation_percentage NUMERIC(5,2) DEFAULT 100.00, is_minor BOOLEAN DEFAULT FALSE, guardian_name VARCHAR(150), req_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
 created_by BIGINT NOT NULL, created_on TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, modified_by BIGINT, modified_on TIMESTAMPTZ,
 verified_by BIGINT, verified_on TIMESTAMPTZ,
 CONSTRAINT fk_mb004014_branch FOREIGN KEY(branch_code) REFERENCES public."MB002002"(branch_code),
 CONSTRAINT fk_mb004014_customer_account FOREIGN KEY(account_no,customer_no) REFERENCES public."MB002003"(account_no,customer_no),
 CONSTRAINT fk_mb004014_created_by FOREIGN KEY(created_by) REFERENCES public."MB001001"(user_id),
 CONSTRAINT fk_mb004014_modified_by FOREIGN KEY(modified_by) REFERENCES public."MB001001"(user_id),
 CONSTRAINT fk_mb004014_verified_by FOREIGN KEY(verified_by) REFERENCES public."MB001001"(user_id),
 CONSTRAINT chk_mb004014_allocation CHECK(allocation_percentage > 0 AND allocation_percentage <= 100)
);

CREATE TABLE IF NOT EXISTS public."MB004015" (
 id BIGSERIAL PRIMARY KEY, customer_no VARCHAR(30) NOT NULL, account_no VARCHAR(30) NOT NULL,
 document_type VARCHAR(50) NOT NULL, document_name VARCHAR(150) NOT NULL, original_file_name VARCHAR(255), stored_file_name VARCHAR(255) NOT NULL,
 file_path TEXT NOT NULL, file_extension VARCHAR(10) NOT NULL, file_size BIGINT, upload_status VARCHAR(20) NOT NULL DEFAULT 'UPLOADED',
 created_by BIGINT NOT NULL, created_on TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, modified_on TIMESTAMPTZ,
 CONSTRAINT fk_mb004015_customer_account FOREIGN KEY(account_no,customer_no) REFERENCES public."MB002003"(account_no,customer_no),
 CONSTRAINT fk_mb004015_created_by FOREIGN KEY(created_by) REFERENCES public."MB001001"(user_id),
 CONSTRAINT chk_mb004015_extension CHECK(LOWER(file_extension) IN ('jpg','jpeg','png')),
 CONSTRAINT chk_mb004015_file_size CHECK(file_size IS NULL OR file_size > 0)
);

CREATE TABLE IF NOT EXISTS public."MB004016" (
 id BIGSERIAL PRIMARY KEY, customer_no VARCHAR(30) NOT NULL, account_no VARCHAR(30) NOT NULL,
 current_step SMALLINT NOT NULL DEFAULT 1, upload_documents BOOLEAN NOT NULL DEFAULT FALSE, verify_details BOOLEAN NOT NULL DEFAULT FALSE,
 review_completed BOOLEAN NOT NULL DEFAULT FALSE, final_approved BOOLEAN NOT NULL DEFAULT FALSE, kyc_status VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
 rejection_reason TEXT, submitted_on TIMESTAMPTZ, verified_by BIGINT, verified_on TIMESTAMPTZ,
 created_on TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, modified_on TIMESTAMPTZ,
 CONSTRAINT fk_mb004016_customer_account FOREIGN KEY(account_no,customer_no) REFERENCES public."MB002003"(account_no,customer_no),
 CONSTRAINT fk_mb004016_verified_by FOREIGN KEY(verified_by) REFERENCES public."MB001001"(user_id),
 CONSTRAINT uq_mb004016_customer_account UNIQUE(customer_no,account_no),
 CONSTRAINT chk_mb004016_step CHECK(current_step BETWEEN 1 AND 4)
);

CREATE TABLE IF NOT EXISTS public."MB004013" (
 id BIGSERIAL PRIMARY KEY, branch_code VARCHAR(20) NOT NULL, customer_no VARCHAR(30) NOT NULL, account_no VARCHAR(30) NOT NULL,
 account_status VARCHAR(20), account_type VARCHAR(20), req_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
 cheque_no VARCHAR(6) NOT NULL, cheque_date DATE NOT NULL, cheque_amount NUMERIC(18,2) NOT NULL, payee_name VARCHAR(150) NOT NULL,
 remarks VARCHAR(255), customer_confirmed BOOLEAN NOT NULL DEFAULT FALSE, match_status VARCHAR(15) NOT NULL DEFAULT 'NOT_PRESENTED',
 mismatch_reason VARCHAR(255), matched_on TIMESTAMPTZ, reject_reason VARCHAR(255),
 created_by BIGINT NOT NULL, created_on TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, modified_by BIGINT, modified_on TIMESTAMPTZ,
 verified_by BIGINT, verified_on TIMESTAMPTZ,
 CONSTRAINT chk_mb004013_req_status CHECK(req_status IN ('PENDING','APPROVED','REJECTED','CANCELLED','COMPLETED','FAILED')),
 CONSTRAINT chk_mb004013_match_status CHECK(match_status IN ('NOT_PRESENTED','MATCHED','MISMATCHED')),
 CONSTRAINT chk_mb004013_cheque_no CHECK(cheque_no ~ '^[0-9]{6}$'), CONSTRAINT chk_mb004013_amount CHECK(cheque_amount > 0),
 CONSTRAINT chk_mb004013_confirmed CHECK(customer_confirmed IS TRUE),
 CONSTRAINT fk_mb004013_branch FOREIGN KEY(branch_code) REFERENCES public."MB002002"(branch_code) ON UPDATE CASCADE ON DELETE RESTRICT,
 CONSTRAINT fk_mb004013_account FOREIGN KEY(account_no,customer_no) REFERENCES public."MB002003"(account_no,customer_no) ON UPDATE CASCADE ON DELETE RESTRICT,
 CONSTRAINT fk_mb004013_created FOREIGN KEY(created_by) REFERENCES public."MB001001"(user_id) ON UPDATE CASCADE ON DELETE RESTRICT,
 CONSTRAINT fk_mb004013_modified FOREIGN KEY(modified_by) REFERENCES public."MB001001"(user_id) ON UPDATE CASCADE ON DELETE RESTRICT,
 CONSTRAINT fk_mb004013_verified FOREIGN KEY(verified_by) REFERENCES public."MB001001"(user_id) ON UPDATE CASCADE ON DELETE RESTRICT,
 CONSTRAINT chk_mb004013_maker_checker CHECK(verified_by IS NULL OR verified_by <> created_by),
 CONSTRAINT chk_mb004013_verify_pair CHECK((verified_by IS NULL) = (verified_on IS NULL))
);
CREATE INDEX IF NOT EXISTS idx_mb004014_customer ON public."MB004014"(customer_no,account_no);
CREATE INDEX IF NOT EXISTS idx_mb004015_customer ON public."MB004015"(customer_no,account_no);
CREATE INDEX IF NOT EXISTS idx_mb004016_status ON public."MB004016"(kyc_status);
CREATE INDEX IF NOT EXISTS idx_mb004013_customer ON public."MB004013"(customer_no,created_on DESC);
CREATE INDEX IF NOT EXISTS idx_mb004013_branch_status ON public."MB004013"(branch_code,req_status);
CREATE UNIQUE INDEX IF NOT EXISTS uq_mb004013_live_cheque ON public."MB004013"(account_no,cheque_no) WHERE req_status IN ('PENDING','APPROVED','COMPLETED');
DROP TRIGGER IF EXISTS trg_mb004013_touch ON public."MB004013";
CREATE TRIGGER trg_mb004013_touch BEFORE UPDATE ON public."MB004013" FOR EACH ROW EXECUTE FUNCTION public.fn_touch_modified_on();
