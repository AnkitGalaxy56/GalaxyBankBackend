-- =====================================================================================
-- 03_mobile_utilities.sql
-- New tables for: Mobile Utilities > Image Uploading, Push Notification, Device Reset.
-- Safe to run on the LIVE database (every statement is idempotent).
-- Run once with:  psql "<your DB_URL>" -f 03_mobile_utilities.sql
-- Then restart the portal API.
--
-- Tables used by the four pages:
--   Image Uploading       MB006001  (new)
--   Push Notification     MB003003  (new: campaigns)  ->  delivers into MB003001 (customer inbox, existing)
--   Device Reset          MB003004  (new: reset history) + MB003002 (existing: device registration)
--   Change Colour Theme   portal_bank_theme (already created by the API / 02_request_handling_additions.sql)
-- =====================================================================================

-- 1. Mobile application images ---------------------------------------------------------
create table if not exists public."MB006001" (
    id            bigserial primary key,
    image_name    varchar(100)  not null,
    image_type    varchar(20)   not null
                  check (image_type in ('Banner','App Logo','Icon','Onboarding','Promotion','Other')),
    image_path    varchar(2048) not null,
    file_size     bigint        not null default 0 check (file_size >= 0),
    file_format   varchar(10),
    dimensions    varchar(20),
    alt_text      varchar(255),
    platform      varchar(10)   not null default 'Both' check (platform in ('Android','iOS','Both')),
    status        varchar(10)   not null default 'Active' check (status in ('Active','Inactive')),
    thumb_data    text,                       -- small preview as a data URL (shown in the list)
    file_data     bytea,                      -- the uploaded image itself (PNG / JPEG / GIF / WebP, max 10 MB)
    file_mime     varchar(50),
    created_by    bigint        not null,
    created_on    timestamptz   not null default now(),
    modified_by   bigint,
    modified_on   timestamptz
);
create unique index if not exists uq_mb006001_name on public."MB006001" (lower(image_name));
create index if not exists idx_mb006001_status     on public."MB006001" (status, image_type);

-- 2. Push notification campaigns -------------------------------------------------------
create table if not exists public."MB003003" (
    id             bigserial primary key,
    title          varchar(80)  not null,
    message        varchar(500) not null,
    branch_code    varchar(20)  references public."MB002002"(branch_code) on update cascade on delete restrict,  -- NULL = all branches
    audience       varchar(40)  not null
                   check (audience in ('All customers','Customers with pending MPIN','Customers with active loans')),
    status         varchar(10)  not null default 'Draft' check (status in ('Draft','Scheduled','Sent')),
    scheduled_for  timestamptz,
    sent_on        timestamptz,
    recipients     integer      not null default 0 check (recipients >= 0),
    sent_by        bigint,
    created_by     bigint       not null,
    created_on     timestamptz  not null default now(),
    modified_by    bigint,
    modified_on    timestamptz,
    constraint chk_mb003003_schedule check (status <> 'Scheduled' or scheduled_for is not null)
);
create index if not exists idx_mb003003_due    on public."MB003003" (scheduled_for) where status = 'Scheduled';
create index if not exists idx_mb003003_branch on public."MB003003" (branch_code, created_on desc);

-- 3. Device reset history --------------------------------------------------------------
create table if not exists public."MB003004" (
    id             bigserial primary key,
    customer_no    varchar(30)  not null,
    branch_code    varchar(20)  references public."MB002002"(branch_code) on update cascade on delete restrict,
    mobile_masked  varchar(20),
    reason         varchar(50)  not null,
    status         varchar(15)  not null default 'completed' check (status in ('completed','in_review')),
    created_by     bigint       not null,
    created_on     timestamptz  not null default now()
);
create index if not exists idx_mb003004_branch   on public."MB003004" (branch_code, created_on desc);
create index if not exists idx_mb003004_customer on public."MB003004" (customer_no, created_on desc);

-- 4. Helps the device lookup (customer number + mobile).
create index if not exists idx_mb003002_customer_mobile on public."MB003002" (customer_no, mobile) where is_active;
