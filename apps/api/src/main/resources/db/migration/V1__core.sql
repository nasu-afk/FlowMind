CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE users (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), email text NOT NULL UNIQUE,
  display_name text NOT NULL, password_hash text, mfa_enabled boolean NOT NULL DEFAULT false,
  status text NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('INVITED','ACTIVE','SUSPENDED','DELETED')),
  created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE organizations (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), name text NOT NULL, slug text NOT NULL UNIQUE,
  created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE organization_members (
  organization_id uuid NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  role text NOT NULL CHECK (role IN ('OWNER','ADMIN','MEMBER','VIEWER')),
  status text NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('INVITED','ACTIVE','SUSPENDED')),
  PRIMARY KEY (organization_id,user_id)
);
CREATE TABLE workspaces (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), organization_id uuid NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
  name text NOT NULL, slug text NOT NULL, created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE(organization_id,slug)
);
CREATE TABLE workspace_members (
  workspace_id uuid NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  role text NOT NULL CHECK (role IN ('OWNER','EDITOR','OPERATOR','VIEWER')),
  status text NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','SUSPENDED')),
  PRIMARY KEY(workspace_id,user_id)
);
CREATE TABLE workflows (
  id uuid PRIMARY KEY, workspace_id uuid NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
  name text NOT NULL, description text, owner_id uuid NOT NULL REFERENCES users(id),
  draft_graph jsonb NOT NULL CHECK (jsonb_typeof(draft_graph)='object'), published_version integer,
  lock_version bigint NOT NULL DEFAULT 0, created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(), deleted_at timestamptz
);
CREATE INDEX workflows_workspace_updated_idx ON workflows(workspace_id,updated_at DESC) WHERE deleted_at IS NULL;
CREATE TABLE workflow_versions (
  id uuid PRIMARY KEY, workflow_id uuid NOT NULL REFERENCES workflows(id) ON DELETE CASCADE,
  version_number integer NOT NULL CHECK(version_number>0), graph jsonb NOT NULL,
  published_by uuid NOT NULL REFERENCES users(id), published_at timestamptz NOT NULL,
  UNIQUE(workflow_id,version_number)
);
CREATE TABLE executions (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), workspace_id uuid NOT NULL REFERENCES workspaces(id),
  workflow_id uuid NOT NULL REFERENCES workflows(id), workflow_version_id uuid NOT NULL REFERENCES workflow_versions(id),
  state text NOT NULL CHECK(state IN ('CREATED','QUEUED','RUNNING','WAITING','PAUSED','COMPLETED','FAILED','CANCELLED','TIMED_OUT')),
  idempotency_key text, input jsonb NOT NULL DEFAULT '{}', output jsonb, error jsonb,
  created_at timestamptz NOT NULL DEFAULT now(), started_at timestamptz, finished_at timestamptz,
  UNIQUE(workspace_id,idempotency_key)
);
CREATE INDEX executions_workspace_created_idx ON executions(workspace_id,created_at DESC);
CREATE TABLE execution_events (
  id bigserial PRIMARY KEY, execution_id uuid NOT NULL REFERENCES executions(id) ON DELETE CASCADE,
  sequence_no bigint NOT NULL, event_type text NOT NULL, payload jsonb NOT NULL DEFAULT '{}', created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE(execution_id,sequence_no)
);
CREATE TABLE execution_checkpoints (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), execution_id uuid NOT NULL REFERENCES executions(id) ON DELETE CASCADE,
  node_id text NOT NULL, attempt integer NOT NULL, state jsonb NOT NULL, created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE(execution_id,node_id,attempt)
);
CREATE TABLE credentials (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), workspace_id uuid NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
  provider text NOT NULL, name text NOT NULL, encrypted_data bytea NOT NULL, encrypted_data_key bytea NOT NULL,
  key_version text NOT NULL, status text NOT NULL DEFAULT 'ACTIVE', created_by uuid NOT NULL REFERENCES users(id),
  created_at timestamptz NOT NULL DEFAULT now(), rotated_at timestamptz, revoked_at timestamptz
);
CREATE TABLE audit_events (
  id bigserial PRIMARY KEY, organization_id uuid REFERENCES organizations(id), workspace_id uuid REFERENCES workspaces(id),
  actor_id uuid, action text NOT NULL, target_type text NOT NULL, target_id text,
  metadata jsonb NOT NULL DEFAULT '{}', occurred_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX audit_tenant_time_idx ON audit_events(organization_id,occurred_at DESC);
CREATE TABLE outbox_events (
  id uuid PRIMARY KEY, aggregate_type text NOT NULL, aggregate_id uuid NOT NULL, event_type text NOT NULL,
  event_version integer NOT NULL, payload jsonb NOT NULL, created_at timestamptz NOT NULL DEFAULT now(),
  published_at timestamptz, attempts integer NOT NULL DEFAULT 0, last_error text
);
CREATE INDEX outbox_unpublished_idx ON outbox_events(created_at) WHERE published_at IS NULL;

