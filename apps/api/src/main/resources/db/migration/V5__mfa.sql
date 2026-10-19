CREATE TABLE user_mfa_totp (
  user_id uuid PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  encrypted_secret bytea NOT NULL,
  encrypted_data_key bytea NOT NULL,
  key_version text NOT NULL,
  confirmed_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE user_mfa_recovery_codes (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  code_hash bytea NOT NULL,
  used_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE(user_id,code_hash)
);
CREATE INDEX user_mfa_recovery_unused_idx ON user_mfa_recovery_codes(user_id) WHERE used_at IS NULL;
