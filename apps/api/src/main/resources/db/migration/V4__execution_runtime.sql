ALTER TABLE approvals ADD CONSTRAINT approvals_execution_node_unique UNIQUE(execution_id,node_id);
ALTER TABLE approvals ADD CONSTRAINT approvals_expiration_policy_check CHECK(expiration_policy IN ('FAIL','CONTINUE'));
CREATE INDEX executions_recovery_idx ON executions(state,started_at) WHERE state IN ('QUEUED','RUNNING','WAITING');
