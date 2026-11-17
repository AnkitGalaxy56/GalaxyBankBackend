-- Allianza Portal: payment source marker and rotating refresh-token storage.
-- Run once against the same PostgreSQL database used by portal-api.

ALTER TABLE public."MB004001"
  ADD COLUMN IF NOT EXISTS is_sys SMALLINT NOT NULL DEFAULT 1;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conname = 'chk_mb004001_is_sys'
      AND conrelid = 'public."MB004001"'::regclass
  ) THEN
    ALTER TABLE public."MB004001"
      ADD CONSTRAINT chk_mb004001_is_sys CHECK (is_sys IN (1, 2));
  END IF;
END $$;

COMMENT ON COLUMN public."MB004001".is_sys IS '1 = Mobile App, 2 = Control Panel';
-- is_sys is SMALLINT: FALSE is not a valid default. Mobile-app rows default to 1.
ALTER TABLE public."MB004001" ALTER COLUMN is_sys SET DEFAULT 1;

CREATE TABLE IF NOT EXISTS public.portal_auth_refresh_tokens (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  token_hash VARCHAR(64) NOT NULL UNIQUE,
  expires_at TIMESTAMPTZ NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  revoked_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_portal_auth_refresh_user
  ON public.portal_auth_refresh_tokens(user_id);
