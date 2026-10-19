CREATE TABLE oidc_identities (
  issuer text NOT NULL,
  subject text NOT NULL,
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  email_at_link text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  last_login_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY(issuer,subject),
  UNIQUE(issuer,user_id)
);

CREATE TABLE oidc_login_codes (
  code_hash bytea PRIMARY KEY,
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  organization_id uuid NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
  workspace_id uuid NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
  expires_at timestamptz NOT NULL,
  used_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX oidc_login_codes_expiry_idx ON oidc_login_codes(expires_at) WHERE used_at IS NULL;
