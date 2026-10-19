package com.flowmind.api.trigger;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class TriggerExecutionService {
    private final JdbcClient jdbc;
    private final ObjectMapper json;

    public TriggerExecutionService(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public UUID dispatch(UUID triggerId, UUID deliveryId, Map<String, Object> suppliedInput) {
        var target = jdbc.sql("SELECT w.id,w.workspace_id,v.id FROM triggers t JOIN workflows w ON w.id=t.workflow_id JOIN workflow_versions v ON v.workflow_id=w.id AND v.version_number=w.published_version WHERE t.id=:trigger AND t.status='ACTIVE' AND w.deleted_at IS NULL")
            .param("trigger", triggerId)
            .query((rs, row) -> new Target(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getObject(3, UUID.class)))
            .optional();
        if (target.isEmpty()) {
            jdbc.sql("UPDATE trigger_deliveries SET status='FAILED' WHERE id=:delivery AND execution_id IS NULL")
                .param("delivery", deliveryId).update();
            return null;
        }

        var input = new HashMap<String, Object>(suppliedInput);
        input.put("triggerId", triggerId.toString());
        input.put("deliveryId", deliveryId.toString());
        UUID executionId = UUID.randomUUID();
        int inserted = jdbc.sql("INSERT INTO executions(id,workspace_id,workflow_id,workflow_version_id,state,idempotency_key,input) VALUES (:id,:workspace,:workflow,:version,'QUEUED',:key,CAST(:input AS jsonb)) ON CONFLICT(workspace_id,idempotency_key) DO NOTHING")
            .param("id", executionId).param("workspace", target.get().workspaceId()).param("workflow", target.get().workflowId())
            .param("version", target.get().versionId()).param("key", "trigger:" + deliveryId).param("input", write(input)).update();
        if (inserted == 0) {
            executionId = jdbc.sql("SELECT id FROM executions WHERE workspace_id=:workspace AND idempotency_key=:key")
                .param("workspace", target.get().workspaceId()).param("key", "trigger:" + deliveryId).query(UUID.class).single();
        } else {
            jdbc.sql("INSERT INTO execution_events(execution_id,sequence_no,event_type,payload) VALUES (:execution,1,'execution.queued',jsonb_build_object('triggerId',CAST(:trigger AS text),'deliveryId',CAST(:delivery AS text)))")
                .param("execution", executionId).param("trigger", triggerId).param("delivery", deliveryId).update();
            jdbc.sql("INSERT INTO outbox_events(id,aggregate_type,aggregate_id,event_type,event_version,payload) VALUES (:id,'execution',:execution,'execution.queued',1,jsonb_build_object('executionId',CAST(:execution AS text),'workspaceId',CAST(:workspace AS text)))")
                .param("id", UUID.randomUUID()).param("execution", executionId).param("workspace", target.get().workspaceId()).update();
        }
        jdbc.sql("UPDATE trigger_deliveries SET status='DISPATCHED',execution_id=:execution WHERE id=:delivery")
            .param("execution", executionId).param("delivery", deliveryId).update();
        return executionId;
    }

    private String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception failure) { throw new IllegalArgumentException("Trigger input is not valid JSON", failure); }
    }

    private record Target(UUID workflowId, UUID workspaceId, UUID versionId) {}
}
