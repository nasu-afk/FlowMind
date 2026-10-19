CREATE TABLE integration_connections (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), workspace_id uuid NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
  provider text NOT NULL, display_name text NOT NULL, credential_id uuid REFERENCES credentials(id),
  status text NOT NULL CHECK(status IN ('AWAITING_CREDENTIALS','CONNECTED','DEGRADED','REVOKED')),
  scopes text[] NOT NULL DEFAULT '{}', external_account_id text, configuration jsonb NOT NULL DEFAULT '{}',
  created_by uuid NOT NULL REFERENCES users(id), created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX integration_connections_workspace_idx ON integration_connections(workspace_id,provider);

CREATE TABLE triggers (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), workflow_id uuid NOT NULL REFERENCES workflows(id) ON DELETE CASCADE,
  type text NOT NULL, configuration jsonb NOT NULL DEFAULT '{}', status text NOT NULL DEFAULT 'ACTIVE' CHECK(status IN ('ACTIVE','PAUSED','DISABLED')),
  secret_hash bytea, created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE schedules (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), trigger_id uuid NOT NULL UNIQUE REFERENCES triggers(id) ON DELETE CASCADE,
  cron_expression text, run_at timestamptz, timezone text NOT NULL DEFAULT 'UTC',
  missed_run_policy text NOT NULL DEFAULT 'SKIP' CHECK(missed_run_policy IN ('SKIP','RUN_ONCE','CATCH_UP_BOUNDED')),
  max_catch_up integer NOT NULL DEFAULT 1 CHECK(max_catch_up BETWEEN 1 AND 100), next_run_at timestamptz, last_run_at timestamptz,
  CHECK ((cron_expression IS NOT NULL) <> (run_at IS NOT NULL))
);
CREATE INDEX schedules_due_idx ON schedules(next_run_at) WHERE next_run_at IS NOT NULL;
CREATE TABLE trigger_deliveries (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), trigger_id uuid NOT NULL REFERENCES triggers(id) ON DELETE CASCADE,
  deduplication_key text NOT NULL, request_metadata jsonb NOT NULL DEFAULT '{}', status text NOT NULL,
  received_at timestamptz NOT NULL DEFAULT now(), execution_id uuid REFERENCES executions(id), UNIQUE(trigger_id,deduplication_key)
);

CREATE TABLE ai_provider_connections (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), workspace_id uuid NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
  provider text NOT NULL, credential_id uuid REFERENCES credentials(id), base_url text, status text NOT NULL,
  allowed_models text[] NOT NULL DEFAULT '{}', created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE prompt_templates (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), workspace_id uuid NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
  name text NOT NULL, current_version integer NOT NULL DEFAULT 0, created_by uuid NOT NULL REFERENCES users(id), created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE prompt_versions (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), prompt_id uuid NOT NULL REFERENCES prompt_templates(id) ON DELETE CASCADE,
  version_number integer NOT NULL, template text NOT NULL, input_schema jsonb, output_schema jsonb,
  created_by uuid NOT NULL REFERENCES users(id), created_at timestamptz NOT NULL DEFAULT now(), UNIQUE(prompt_id,version_number)
);
CREATE TABLE ai_agents (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), workspace_id uuid NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
  name text NOT NULL, instructions text NOT NULL, provider_connection_id uuid REFERENCES ai_provider_connections(id), model text NOT NULL,
  tool_permissions jsonb NOT NULL DEFAULT '[]', memory_strategy text NOT NULL DEFAULT 'NONE', output_schema jsonb,
  max_iterations integer NOT NULL DEFAULT 8 CHECK(max_iterations BETWEEN 1 AND 100), token_limit integer NOT NULL CHECK(token_limit>0),
  timeout_seconds integer NOT NULL CHECK(timeout_seconds BETWEEN 1 AND 3600), requires_approval boolean NOT NULL DEFAULT false,
  created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE agent_sessions (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), agent_id uuid NOT NULL REFERENCES ai_agents(id) ON DELETE CASCADE,
  execution_id uuid REFERENCES executions(id), state jsonb NOT NULL DEFAULT '{}', step_count integer NOT NULL DEFAULT 0,
  tokens_used bigint NOT NULL DEFAULT 0, estimated_cost numeric(18,8) NOT NULL DEFAULT 0,
  status text NOT NULL, created_at timestamptz NOT NULL DEFAULT now(), finished_at timestamptz
);

CREATE TABLE knowledge_bases (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), workspace_id uuid NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
  name text NOT NULL, embedding_provider_id uuid REFERENCES ai_provider_connections(id), embedding_model text NOT NULL,
  created_by uuid NOT NULL REFERENCES users(id), created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE documents (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), knowledge_base_id uuid NOT NULL REFERENCES knowledge_bases(id) ON DELETE CASCADE,
  version integer NOT NULL DEFAULT 1, filename text NOT NULL, media_type text NOT NULL, object_key text NOT NULL,
  sha256 text NOT NULL, size_bytes bigint NOT NULL CHECK(size_bytes>=0), status text NOT NULL,
  metadata jsonb NOT NULL DEFAULT '{}', uploaded_by uuid NOT NULL REFERENCES users(id), created_at timestamptz NOT NULL DEFAULT now(), deleted_at timestamptz,
  UNIQUE(knowledge_base_id,sha256,version)
);
CREATE TABLE document_chunks (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), document_id uuid NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
  ordinal integer NOT NULL, content text NOT NULL, token_count integer NOT NULL CHECK(token_count>=0), metadata jsonb NOT NULL DEFAULT '{}',
  embedding vector(1536), UNIQUE(document_id,ordinal)
);
CREATE INDEX document_chunks_embedding_idx ON document_chunks USING hnsw (embedding vector_cosine_ops);

CREATE TABLE approvals (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), execution_id uuid NOT NULL REFERENCES executions(id) ON DELETE CASCADE,
  node_id text NOT NULL, assigned_user_id uuid REFERENCES users(id), assigned_role text,
  status text NOT NULL CHECK(status IN ('PENDING','APPROVED','REJECTED','EXPIRED','CANCELLED')),
  expires_at timestamptz, expiration_policy text NOT NULL DEFAULT 'FAIL', decision_comment text,
  decided_by uuid REFERENCES users(id), decided_at timestamptz, created_at timestamptz NOT NULL DEFAULT now(),
  CHECK(assigned_user_id IS NOT NULL OR assigned_role IS NOT NULL)
);
CREATE INDEX approvals_pending_idx ON approvals(expires_at) WHERE status='PENDING';

CREATE TABLE workflow_comments (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), workflow_id uuid NOT NULL REFERENCES workflows(id) ON DELETE CASCADE,
  author_id uuid NOT NULL REFERENCES users(id), parent_id uuid REFERENCES workflow_comments(id), node_id text,
  body text NOT NULL, created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(), resolved_at timestamptz
);
CREATE TABLE activity_events (
  id bigserial PRIMARY KEY, workspace_id uuid NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
  actor_id uuid REFERENCES users(id), type text NOT NULL, subject_type text NOT NULL, subject_id text NOT NULL,
  payload jsonb NOT NULL DEFAULT '{}', occurred_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX activity_workspace_time_idx ON activity_events(workspace_id,occurred_at DESC);

CREATE TABLE templates (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), owner_organization_id uuid REFERENCES organizations(id),
  slug text NOT NULL UNIQUE, name text NOT NULL, description text NOT NULL, category text NOT NULL,
  visibility text NOT NULL CHECK(visibility IN ('PUBLIC','PRIVATE','UNLISTED')), status text NOT NULL DEFAULT 'DRAFT',
  featured boolean NOT NULL DEFAULT false, created_by uuid NOT NULL REFERENCES users(id), created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE template_versions (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), template_id uuid NOT NULL REFERENCES templates(id) ON DELETE CASCADE,
  version_number integer NOT NULL, graph jsonb NOT NULL, changelog text, created_at timestamptz NOT NULL DEFAULT now(), UNIQUE(template_id,version_number)
);
CREATE TABLE template_reviews (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), template_id uuid NOT NULL REFERENCES templates(id) ON DELETE CASCADE,
  user_id uuid NOT NULL REFERENCES users(id), rating integer NOT NULL CHECK(rating BETWEEN 1 AND 5), body text,
  created_at timestamptz NOT NULL DEFAULT now(), UNIQUE(template_id,user_id)
);

CREATE TABLE notifications (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  workspace_id uuid REFERENCES workspaces(id) ON DELETE CASCADE, type text NOT NULL, title text NOT NULL, body text NOT NULL,
  action_url text, read_at timestamptz, created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX notifications_user_unread_idx ON notifications(user_id,created_at DESC) WHERE read_at IS NULL;
CREATE TABLE notification_deliveries (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), notification_id uuid NOT NULL REFERENCES notifications(id) ON DELETE CASCADE,
  channel text NOT NULL, status text NOT NULL, attempt integer NOT NULL DEFAULT 1, provider_message_id text,
  last_error text, attempted_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE notification_preferences (
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE, type text NOT NULL,
  in_app boolean NOT NULL DEFAULT true, email boolean NOT NULL DEFAULT true, PRIMARY KEY(user_id,type)
);

CREATE TABLE usage_events (
  id bigserial PRIMARY KEY, organization_id uuid NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
  workspace_id uuid REFERENCES workspaces(id) ON DELETE CASCADE, execution_id uuid REFERENCES executions(id) ON DELETE SET NULL,
  metric text NOT NULL, quantity numeric(20,6) NOT NULL CHECK(quantity>=0), dimensions jsonb NOT NULL DEFAULT '{}',
  occurred_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX usage_tenant_metric_time_idx ON usage_events(organization_id,metric,occurred_at DESC);
CREATE TABLE api_keys (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), organization_id uuid NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
  name text NOT NULL, key_prefix text NOT NULL, secret_hash bytea NOT NULL, scopes text[] NOT NULL DEFAULT '{}',
  created_by uuid NOT NULL REFERENCES users(id), created_at timestamptz NOT NULL DEFAULT now(), last_used_at timestamptz, expires_at timestamptz, revoked_at timestamptz
);
CREATE UNIQUE INDEX api_keys_prefix_idx ON api_keys(key_prefix);
CREATE TABLE feature_flags (
  key text PRIMARY KEY, description text NOT NULL, enabled boolean NOT NULL DEFAULT false,
  targeting jsonb NOT NULL DEFAULT '{}', updated_by uuid REFERENCES users(id), updated_at timestamptz NOT NULL DEFAULT now()
);

