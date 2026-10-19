CREATE TABLE auth_sessions (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  refresh_token_hash bytea NOT NULL UNIQUE, user_agent text, ip_hash bytea,
  created_at timestamptz NOT NULL DEFAULT now(), expires_at timestamptz NOT NULL,
  rotated_at timestamptz, revoked_at timestamptz
);
CREATE INDEX auth_sessions_user_active_idx ON auth_sessions(user_id,expires_at) WHERE revoked_at IS NULL;
CREATE TABLE password_reset_tokens (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash bytea NOT NULL UNIQUE, expires_at timestamptz NOT NULL, used_at timestamptz, created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE mfa_factors (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  type text NOT NULL CHECK(type IN ('TOTP','WEBAUTHN','RECOVERY_CODE')), label text,
  encrypted_secret bytea, public_data jsonb, created_at timestamptz NOT NULL DEFAULT now(), verified_at timestamptz, revoked_at timestamptz
);

