-- Allianza Portal: user management, password and role/screen access support.
-- Run once against the same PostgreSQL database that contains MB001001 and MB001002.

CREATE TABLE IF NOT EXISTS public.portal_access_roles (
    id varchar(64) PRIMARY KEY,
    role_name varchar(60) NOT NULL UNIQUE,
    description varchar(250) NOT NULL,
    active boolean NOT NULL DEFAULT true,
    system_role boolean NOT NULL DEFAULT false,
    permissions jsonb NOT NULL DEFAULT '[]'::jsonb,
    created_by bigint,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz
);

CREATE INDEX IF NOT EXISTS idx_portal_access_roles_name_lower
    ON public.portal_access_roles (lower(role_name));

-- MB001001 and MB001002 are the source of truth for user records and password hashes.
-- No plaintext passwords are stored. The API writes BCrypt hashes to MB001002.
-- Screen permissions are stored as JSON: [{"module":"User Master","actions":["view","edit"]}].

-- Default role-to-screen grants. Administrators can subsequently create custom roles
-- and adjust individual screen permissions in Settings > Roles and Access Control.
INSERT INTO public.portal_access_roles (id, role_name, description, active, system_role, permissions)
VALUES
('system-super-admin', 'SUPER_ADMIN', 'System administrator with access to all portal screens.', true, true,
 '[{"module":"Dashboard","actions":["view"]},{"module":"Request Handling","actions":["view"]},{"module":"Masters","actions":["view"]},{"module":"Mobile Utilities","actions":["view"]},{"module":"User Management","actions":["view"]},{"module":"Reports","actions":["view"]},{"module":"Settings","actions":["view"]},{"module":"Bank Master","actions":["view","create","edit","delete"]},{"module":"Branch Master","actions":["view","create","edit","delete"]},{"module":"User Master","actions":["view","create","edit"]},{"module":"Assign Password","actions":["view","edit"]},{"module":"Payments","actions":["view","create","edit","approve"]},{"module":"Cheque Book","actions":["view","create","edit","approve"]},{"module":"Stop Payment","actions":["view","create","edit","approve"]},{"module":"FD Opening","actions":["view","create","edit","approve"]},{"module":"RD Opening","actions":["view","create","edit","approve"]},{"module":"FD / RD Closing","actions":["view","create","edit","approve"]},{"module":"Locker Application","actions":["view","create","edit","approve"]},{"module":"Standing Instructions","actions":["view","create","edit","approve"]},{"module":"Fresh Loan Application","actions":["view","create","edit","approve"]},{"module":"Loan EMI Payment","actions":["view","create","edit","approve"]},{"module":"Image Uploading","actions":["view","create","edit","delete"]},{"module":"Push Notification","actions":["view","create","edit","delete"]},{"module":"Device Reset","actions":["view","create","edit"]},{"module":"Requests","actions":["view"]},{"module":"User Master Report","actions":["view"]},{"module":"Notification","actions":["view"]},{"module":"Change Password","actions":["view","edit"]},{"module":"Profile","actions":["view","edit"]},{"module":"Roles and Access Control","actions":["view","create","edit","delete"]},{"module":"Change Colour Theme","actions":["view","edit"]},{"module":"Banners & Content","actions":["view","create","edit","delete"]}]'::jsonb),
('system-admin', 'ADMIN', 'Bank administrator with access to administration and banking operations.', true, true,
 '[{"module":"Dashboard","actions":["view"]},{"module":"Request Handling","actions":["view"]},{"module":"Masters","actions":["view"]},{"module":"Mobile Utilities","actions":["view"]},{"module":"User Management","actions":["view"]},{"module":"Reports","actions":["view"]},{"module":"Settings","actions":["view"]},{"module":"Bank Master","actions":["view","create","edit","delete"]},{"module":"Branch Master","actions":["view","create","edit","delete"]},{"module":"User Master","actions":["view","create","edit"]},{"module":"Assign Password","actions":["view","edit"]},{"module":"Payments","actions":["view","create","edit","approve"]},{"module":"Cheque Book","actions":["view","create","edit","approve"]},{"module":"Stop Payment","actions":["view","create","edit","approve"]},{"module":"FD Opening","actions":["view","create","edit","approve"]},{"module":"RD Opening","actions":["view","create","edit","approve"]},{"module":"FD / RD Closing","actions":["view","create","edit","approve"]},{"module":"Locker Application","actions":["view","create","edit","approve"]},{"module":"Standing Instructions","actions":["view","create","edit","approve"]},{"module":"Fresh Loan Application","actions":["view","create","edit","approve"]},{"module":"Loan EMI Payment","actions":["view","create","edit","approve"]},{"module":"Image Uploading","actions":["view","create","edit","delete"]},{"module":"Push Notification","actions":["view","create","edit","delete"]},{"module":"Device Reset","actions":["view","create","edit"]},{"module":"Requests","actions":["view"]},{"module":"User Master Report","actions":["view"]},{"module":"Notification","actions":["view"]},{"module":"Change Password","actions":["view","edit"]},{"module":"Profile","actions":["view","edit"]},{"module":"Roles and Access Control","actions":["view","create","edit","delete"]},{"module":"Change Colour Theme","actions":["view","edit"]},{"module":"Banners & Content","actions":["view","create","edit","delete"]}]'::jsonb),
('system-bank-user', 'STAFF', 'Bank staff with access to operational request and report screens.', true, true,
 '[{"module":"Dashboard","actions":["view"]},{"module":"Request Handling","actions":["view"]},{"module":"Payments","actions":["view","create","edit"]},{"module":"Cheque Book","actions":["view","create","edit"]},{"module":"Stop Payment","actions":["view","create","edit"]},{"module":"FD Opening","actions":["view","create","edit"]},{"module":"RD Opening","actions":["view","create","edit"]},{"module":"FD / RD Closing","actions":["view","create","edit"]},{"module":"Locker Application","actions":["view","create","edit"]},{"module":"Standing Instructions","actions":["view","create","edit"]},{"module":"Fresh Loan Application","actions":["view","create","edit"]},{"module":"Loan EMI Payment","actions":["view","create","edit"]},{"module":"Requests","actions":["view"]},{"module":"User Master Report","actions":["view"]},{"module":"Notification","actions":["view"]},{"module":"Change Password","actions":["view","edit"]},{"module":"Profile","actions":["view","edit"]}]'::jsonb),
('system-bank-user-role', 'BANK_USER', 'Bank user with operational request and profile access.', true, true,
 '[{"module":"Dashboard","actions":["view"]},{"module":"Request Handling","actions":["view"]},{"module":"Payments","actions":["view","create","edit"]},{"module":"Cheque Book","actions":["view","create","edit"]},{"module":"Stop Payment","actions":["view","create","edit"]},{"module":"FD Opening","actions":["view","create","edit"]},{"module":"RD Opening","actions":["view","create","edit"]},{"module":"FD / RD Closing","actions":["view","create","edit"]},{"module":"Locker Application","actions":["view","create","edit"]},{"module":"Standing Instructions","actions":["view","create","edit"]},{"module":"Fresh Loan Application","actions":["view","create","edit"]},{"module":"Loan EMI Payment","actions":["view","create","edit"]},{"module":"Requests","actions":["view"]},{"module":"User Master Report","actions":["view"]},{"module":"Notification","actions":["view"]},{"module":"Change Password","actions":["view","edit"]},{"module":"Profile","actions":["view","edit"]}]'::jsonb)
ON CONFLICT (role_name) DO NOTHING;

-- The User Master form allows an idle timeout up to 86,400 seconds; the original
-- backup declared this column SMALLINT (maximum 32,767), so widen it to INTEGER.
ALTER TABLE public."MB001001"
    ALTER COLUMN idle_timeout TYPE integer
    USING idle_timeout::integer;
