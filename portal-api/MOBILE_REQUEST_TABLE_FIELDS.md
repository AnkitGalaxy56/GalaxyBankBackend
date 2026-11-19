# Mobile request database field reference

## MB004014 — Nomination Details
| Field | Type | Required | Purpose |
|---|---|---:|---|
| id | BIGSERIAL | Yes | Primary key |
| branch_code | VARCHAR(20) | Yes | Branch reference |
| customer_no | VARCHAR(30) | Yes | Customer identifier |
| account_no | VARCHAR(30) | Yes | Account reference |
| nominee_name | VARCHAR(150) | Yes | Nominee full name |
| relationship | VARCHAR(50) | Yes | Relationship to account holder |
| date_of_birth | DATE | No | Nominee DOB |
| mobile_no | VARCHAR(15) | No | Nominee phone |
| address | TEXT | No | Nominee address |
| allocation_percentage | NUMERIC(5,2) | No | Allocation (default 100%) |
| is_minor | BOOLEAN | No | Minor flag |
| guardian_name | VARCHAR(150) | No | Guardian if nominee minor |
| req_status | VARCHAR(20) | Yes | Workflow status, default PENDING |
| created_by / created_on | BIGINT / TIMESTAMPTZ | Yes | Maker and timestamp |
| modified_by / modified_on | BIGINT / TIMESTAMPTZ | No | Last edit audit |
| verified_by / verified_on | BIGINT / TIMESTAMPTZ | No | Checker audit |

## MB004015 — KYC Documents
| Field | Type | Required | Purpose |
|---|---|---:|---|
| id | BIGSERIAL | Yes | Primary key |
| customer_no / account_no | VARCHAR(30) | Yes | Customer/account reference |
| document_type | VARCHAR(50) | Yes | Document category |
| document_name | VARCHAR(150) | Yes | Display name |
| original_file_name | VARCHAR(255) | No | Client file name |
| stored_file_name | VARCHAR(255) | Yes | Stored name |
| file_path | TEXT | Yes | Storage location |
| file_extension | VARCHAR(10) | Yes | jpg/jpeg/png |
| file_size | BIGINT | No | Size in bytes |
| upload_status | VARCHAR(20) | Yes | Default UPLOADED |
| created_by / created_on | BIGINT / TIMESTAMPTZ | Yes | Upload audit |
| modified_on | TIMESTAMPTZ | No | Last modified timestamp |

## MB004016 — KYC Verification / Progress
| Field | Type | Required | Purpose |
|---|---|---:|---|
| id | BIGSERIAL | Yes | Primary key |
| customer_no / account_no | VARCHAR(30) | Yes | Customer/account reference; unique pair |
| current_step | SMALLINT | Yes | 1–4 workflow step |
| upload_documents | BOOLEAN | Yes | Upload step complete |
| verify_details | BOOLEAN | Yes | Details verification complete |
| review_completed | BOOLEAN | Yes | Review complete |
| final_approved | BOOLEAN | Yes | Final approval flag |
| kyc_status | VARCHAR(20) | Yes | Default IN_PROGRESS |
| rejection_reason | TEXT | No | Rejection note |
| submitted_on | TIMESTAMPTZ | No | Submitted time |
| verified_by / verified_on | BIGINT / TIMESTAMPTZ | No | Checker audit |
| created_on / modified_on | TIMESTAMPTZ | Yes / No | Record timestamps |

## MB004013 — Positive Pay Instruction
| Field | Type | Required | Purpose |
|---|---|---:|---|
| id | BIGSERIAL | Yes | Primary key |
| branch_code / customer_no / account_no | VARCHAR | Yes | Branch/customer/account references |
| account_status / account_type | VARCHAR(20) | No | Account attributes |
| req_status | VARCHAR(20) | Yes | Workflow status, default PENDING |
| cheque_no | VARCHAR(6) | Yes | Six-digit cheque number |
| cheque_date | DATE | Yes | Cheque date |
| cheque_amount | NUMERIC(18,2) | Yes | Amount greater than zero |
| payee_name | VARCHAR(150) | Yes | Payee |
| remarks | VARCHAR(255) | No | Optional remarks |
| customer_confirmed | BOOLEAN | Yes | Customer declaration, true required |
| match_status | VARCHAR(15) | Yes | NOT_PRESENTED / MATCHED / MISMATCHED |
| mismatch_reason / matched_on | VARCHAR / TIMESTAMPTZ | No | Matching audit |
| reject_reason | VARCHAR(255) | No | Reject reason |
| created_by / created_on | BIGINT / TIMESTAMPTZ | Yes | Maker audit |
| modified_by / modified_on | BIGINT / TIMESTAMPTZ | No | Last edit audit |
| verified_by / verified_on | BIGINT / TIMESTAMPTZ | No | Checker audit |

## APIs added
- `POST /auth/verify-session` — verify current authenticated user's login ID/password only.
- `GET /mobile-requests/nomination`
- `GET /mobile-requests/kyc`
- `GET /mobile-requests/positive-pay`
- `GET /mobile-requests/{module}/{id}`
- `PATCH /mobile-requests/{module}/{id}/verify`
- `PATCH /mobile-requests/{module}/{id}/reject` (body: `reason`)

Migration: `sql/05_mobile_requests.sql`.
