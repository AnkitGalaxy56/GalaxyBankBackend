CREATE TABLE IF NOT EXISTS public.mobile_auth_sessions (
    session_id UUID PRIMARY KEY,
    user_id BIGINT NOT NULL,
    customer_id VARCHAR(100) NOT NULL,
    device_id VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_used_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_mobile_auth_sessions_user
ON public.mobile_auth_sessions(user_id);

CREATE TABLE IF NOT EXISTS public.mobile_auth_refresh_tokens (
    id BIGSERIAL PRIMARY KEY,
    session_id UUID NOT NULL
        REFERENCES public.mobile_auth_sessions(session_id),
    user_id BIGINT NOT NULL,
    customer_id VARCHAR(100) NOT NULL,
    device_id VARCHAR(128) NOT NULL,
    token_hash CHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_mobile_refresh_session
ON public.mobile_auth_refresh_tokens(session_id);

CREATE INDEX IF NOT EXISTS idx_mobile_refresh_user_device
ON public.mobile_auth_refresh_tokens(user_id, device_id);


BEGIN;

UPDATE public.mobile_auth_sessions
SET revoked_at = NOW()
WHERE session_id = 'fe4f03a0-7c9d-4d4f-915c-0b7db6e4605e'
AND revoked_at IS NULL;

UPDATE public.mobile_auth_refresh_tokens
SET revoked_at = NOW()
WHERE session_id = 'fe4f03a0-7c9d-4d4f-915c-0b7db6e4605e'
AND revoked_at IS NULL;

COMMIT;


ALTER TABLE public."MB004011"
DROP CONSTRAINT mb004011_status_check;

ALTER TABLE public."MB004011"
ADD CONSTRAINT mb004011_status_check
CHECK (
    req_status IN (
        'PENDING',
        'APPROVED',
        'REJECTED',
        'CANCELLED'
    )
);

ALTER TABLE public."MB004001"
ADD COLUMN IF NOT EXISTS customer_confirmed_by BIGINT,
ADD COLUMN IF NOT EXISTS customer_confirmed_on TIMESTAMPTZ;