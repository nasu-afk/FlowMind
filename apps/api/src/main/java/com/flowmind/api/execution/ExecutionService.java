package com.flowmind.api.execution;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.Map;
import java.util.List;
import java.util.UUID;

@Service
public class ExecutionService {
    private final JdbcClient jdbc;
    private final ObjectMapper json;
    public ExecutionService(JdbcClient jdbc, ObjectMapper json) { this.jdbc=jdbc; this.json=json; }

    @Transactional
    public ExecutionView start(UUID workspaceId, UUID workflowId, UUID actorId, StartExecution request) {
        boolean allowed = jdbc.sql("SELECT EXISTS(SELECT 1 FROM workspace_members WHERE workspace_id=:w AND user_id=:u AND status='ACTIVE' AND role IN ('OWNER','EDITOR','OPERATOR'))")
            .param("w",workspaceId).param("u",actorId).query(Boolean.class).single();
        if (!allowed) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        if (request.idempotencyKey()!=null) {
            var existing = jdbc.sql("SELECT id,state,created_at FROM executions WHERE workspace_id=:w AND idempotency_key=:k")
                .param("w",workspaceId).param("k",request.idempotencyKey()).query((rs,row)->new ExecutionView(rs.getObject(1,UUID.class), ExecutionState.valueOf(rs.getString(2)),rs.getTimestamp(3).toInstant())).optional();
            if (existing.isPresent()) return existing.get();
        }
        UUID versionId = jdbc.sql("SELECT v.id FROM workflows w JOIN workflow_versions v ON v.workflow_id=w.id AND v.version_number=w.published_version WHERE w.id=:id AND w.workspace_id=:workspace")
            .param("id",workflowId).param("workspace",workspaceId).query(UUID.class).optional()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,"Workflow must have a published version"));
        UUID executionId=UUID.randomUUID();
        jdbc.sql("INSERT INTO executions(id,workspace_id,workflow_id,workflow_version_id,state,idempotency_key,input) VALUES (:id,:w,:workflow,:version,'QUEUED',:key,CAST(:input AS jsonb))")
            .param("id",executionId).param("w",workspaceId).param("workflow",workflowId).param("version",versionId).param("key",request.idempotencyKey()).param("input",write(request.input())).update();
        appendEvent(executionId,"execution.queued",Map.of("actorId",actorId));
        jdbc.sql("INSERT INTO outbox_events(id,aggregate_type,aggregate_id,event_type,event_version,payload) VALUES (:id,'execution',:execution,'execution.queued',1,CAST(:payload AS jsonb))")
            .param("id",UUID.randomUUID()).param("execution",executionId).param("payload",write(Map.of("executionId",executionId,"workspaceId",workspaceId))).update();
        return new ExecutionView(executionId,ExecutionState.QUEUED,Instant.now());
    }

    @Transactional(readOnly = true)
    public ExecutionDetails get(UUID workspaceId, UUID executionId, UUID actorId) {
        requireMember(workspaceId, actorId);
        return jdbc.sql("SELECT id,workflow_id,state,input::text,output::text,error::text,created_at,started_at,finished_at FROM executions WHERE id=:id AND workspace_id=:workspace")
            .param("id", executionId).param("workspace", workspaceId)
            .query((rs,row) -> new ExecutionDetails(rs.getObject("id",UUID.class),rs.getObject("workflow_id",UUID.class),ExecutionState.valueOf(rs.getString("state")),readMap(rs.getString("input")),readMap(rs.getString("output")),readMap(rs.getString("error")),rs.getTimestamp("created_at").toInstant(),instant(rs.getTimestamp("started_at")),instant(rs.getTimestamp("finished_at"))))
            .optional().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public List<ExecutionView> list(UUID workspaceId, UUID actorId) {
        requireMember(workspaceId, actorId);
        return jdbc.sql("SELECT id,state,created_at FROM executions WHERE workspace_id=:workspace ORDER BY created_at DESC LIMIT 100")
            .param("workspace", workspaceId).query((rs,row)->new ExecutionView(rs.getObject(1,UUID.class),ExecutionState.valueOf(rs.getString(2)),rs.getTimestamp(3).toInstant())).list();
    }

    @Transactional
    public ExecutionView cancel(UUID workspaceId, UUID executionId, UUID actorId, Map<String,Object> details) {
        boolean allowed = jdbc.sql("SELECT EXISTS(SELECT 1 FROM workspace_members WHERE workspace_id=:w AND user_id=:u AND status='ACTIVE' AND role IN ('OWNER','EDITOR','OPERATOR'))")
            .param("w",workspaceId).param("u",actorId).query(Boolean.class).single();
        if (!allowed) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        var current = jdbc.sql("SELECT state,created_at FROM executions WHERE id=:id AND workspace_id=:w FOR UPDATE")
            .param("id",executionId).param("w",workspaceId).query((rs,row)->new ExecutionView(executionId,ExecutionState.valueOf(rs.getString(1)),rs.getTimestamp(2).toInstant())).optional()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        ExecutionState target=ExecutionState.CANCELLED;
        if (!current.state().canTransitionTo(target)) throw new ResponseStatusException(HttpStatus.CONFLICT,"Execution cannot be cancelled from "+current.state());
        jdbc.sql("UPDATE executions SET state=:state,started_at=CASE WHEN :state='RUNNING' AND started_at IS NULL THEN now() ELSE started_at END,finished_at=CASE WHEN :state IN ('COMPLETED','FAILED','CANCELLED','TIMED_OUT') THEN now() ELSE finished_at END WHERE id=:id")
            .param("state",target.name()).param("id",executionId).update();
        var eventDetails=new java.util.HashMap<String,Object>(details==null?Map.of():details); eventDetails.put("actorId",actorId);
        appendEvent(executionId,"execution."+target.name().toLowerCase(),eventDetails);
        return new ExecutionView(executionId,target,current.createdAt());
    }

    private void appendEvent(UUID executionId,String type,Map<String,Object> details) {
        jdbc.sql("INSERT INTO execution_events(execution_id,sequence_no,event_type,payload) SELECT :id,COALESCE(MAX(sequence_no),0)+1,:type,CAST(:payload AS jsonb) FROM execution_events WHERE execution_id=:id")
            .param("id",executionId).param("type",type).param("payload",write(details)).update();
    }
    private void requireMember(UUID workspaceId,UUID actorId) {
        boolean allowed=jdbc.sql("SELECT EXISTS(SELECT 1 FROM workspace_members WHERE workspace_id=:w AND user_id=:u AND status='ACTIVE')").param("w",workspaceId).param("u",actorId).query(Boolean.class).single();
        if(!allowed) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }
    private Map<String,Object> readMap(String value) { try { return value==null?null:json.readValue(value,new TypeReference<>(){}); } catch(JsonProcessingException e) { throw new IllegalStateException(e); } }
    private Instant instant(java.sql.Timestamp value) { return value==null?null:value.toInstant(); }
    private String write(Object value) { try { return json.writeValueAsString(value); } catch(JsonProcessingException e) { throw new IllegalArgumentException(e); } }
    public record StartExecution(String idempotencyKey, Map<String,Object> input) { public StartExecution { input=input==null?Map.of():Map.copyOf(input); } }
    public record ExecutionView(UUID id, ExecutionState state, Instant createdAt) {}
    public record ExecutionDetails(UUID id,UUID workflowId,ExecutionState state,Map<String,Object> input,Map<String,Object> output,Map<String,Object> error,Instant createdAt,Instant startedAt,Instant finishedAt) {}
}

