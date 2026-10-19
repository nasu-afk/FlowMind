package com.flowmind.api.workflow;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class WorkflowService {
    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;
    private final WorkflowGraphValidator validator;

    public WorkflowService(JdbcClient jdbc, ObjectMapper objectMapper, WorkflowGraphValidator validator) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    @Transactional
    public WorkflowView create(UUID workspaceId, UUID actorId, CreateWorkflow request) {
        requireWorkspaceEditor(workspaceId, actorId);
        var validation = validator.validate(request.graph());
        if (!validation.valid()) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, String.join("; ", validation.errors()));
        var id = UUID.randomUUID();
        jdbc.sql("INSERT INTO workflows(id, workspace_id, name, description, owner_id, draft_graph) VALUES (:id,:workspace,:name,:description,:owner,CAST(:graph AS jsonb))")
            .param("id", id).param("workspace", workspaceId).param("name", request.name())
            .param("description", request.description()).param("owner", actorId).param("graph", json(request.graph())).update();
        return new WorkflowView(id, workspaceId, request.name(), request.description(), request.graph(), null, 0, Instant.now());
    }

    @Transactional(readOnly = true)
    public List<WorkflowSummary> list(UUID workspaceId, UUID actorId) {
        requireWorkspaceMember(workspaceId, actorId);
        return jdbc.sql("SELECT id,name,description,published_version,lock_version,updated_at FROM workflows WHERE workspace_id=:workspace AND deleted_at IS NULL ORDER BY updated_at DESC")
            .param("workspace", workspaceId)
            .query((rs, row) -> new WorkflowSummary(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("description"), (Integer) rs.getObject("published_version"), rs.getLong("lock_version"), rs.getTimestamp("updated_at").toInstant()))
            .list();
    }

    @Transactional(readOnly = true)
    public WorkflowView get(UUID workspaceId, UUID workflowId, UUID actorId) {
        requireWorkspaceMember(workspaceId, actorId);
        return load(workspaceId, workflowId);
    }

    @Transactional
    public WorkflowView update(UUID workspaceId, UUID workflowId, UUID actorId, UpdateWorkflow request) {
        requireWorkspaceEditor(workspaceId, actorId);
        var validation = validator.validate(request.graph());
        if (!validation.valid()) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, String.join("; ", validation.errors()));
        int updated = jdbc.sql("UPDATE workflows SET name=:name,description=:description,draft_graph=CAST(:graph AS jsonb),lock_version=lock_version+1,updated_at=now() WHERE id=:id AND workspace_id=:workspace AND deleted_at IS NULL AND lock_version=:version")
            .param("name", request.name()).param("description", request.description()).param("graph", json(request.graph()))
            .param("id", workflowId).param("workspace", workspaceId).param("version", request.lockVersion()).update();
        if (updated == 0) {
            boolean exists = jdbc.sql("SELECT EXISTS(SELECT 1 FROM workflows WHERE id=:id AND workspace_id=:workspace AND deleted_at IS NULL)")
                .param("id", workflowId).param("workspace", workspaceId).query(Boolean.class).single();
            if (!exists) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Workflow was changed by another editor; reload before saving");
        }
        return load(workspaceId, workflowId);
    }

    private WorkflowView load(UUID workspaceId, UUID workflowId) {
        return jdbc.sql("SELECT id, workspace_id, name, description, draft_graph::text, published_version, lock_version, updated_at FROM workflows WHERE id=:id AND workspace_id=:workspace AND deleted_at IS NULL")
            .param("id", workflowId).param("workspace", workspaceId)
            .query((rs, row) -> new WorkflowView(rs.getObject("id", UUID.class), rs.getObject("workspace_id", UUID.class), rs.getString("name"), rs.getString("description"), readGraph(rs.getString("draft_graph")), (Integer) rs.getObject("published_version"), rs.getLong("lock_version"), rs.getTimestamp("updated_at").toInstant()))
            .optional().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @Transactional
    public PublishedWorkflow publish(UUID workspaceId, UUID workflowId, UUID actorId) {
        requireWorkspaceEditor(workspaceId, actorId);
        var workflow = load(workspaceId, workflowId);
        var validation = validator.validate(workflow.graph());
        if (!validation.valid()) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, String.join("; ", validation.errors()));
        jdbc.sql("SELECT id FROM workflows WHERE id=:id AND workspace_id=:workspace FOR UPDATE")
            .param("id", workflowId).param("workspace", workspaceId).query(UUID.class).single();
        int version = jdbc.sql("SELECT COALESCE(MAX(version_number),0)+1 FROM workflow_versions WHERE workflow_id=:id")
            .param("id", workflowId).query(Integer.class).single();
        var versionId = UUID.randomUUID();
        jdbc.sql("INSERT INTO workflow_versions(id,workflow_id,version_number,graph,published_by,published_at) VALUES (:id,:workflow,:version,CAST(:graph AS jsonb),:actor,now())")
            .param("id", versionId).param("workflow", workflowId).param("version", version).param("graph", json(workflow.graph())).param("actor", actorId).update();
        jdbc.sql("UPDATE workflows SET published_version=:version, updated_at=now() WHERE id=:id AND workspace_id=:workspace")
            .param("version", version).param("id", workflowId).param("workspace", workspaceId).update();
        jdbc.sql("INSERT INTO outbox_events(id,aggregate_type,aggregate_id,event_type,event_version,payload) VALUES (:id,'workflow',:aggregate,'workflow.published',1,CAST(:payload AS jsonb))")
            .param("id", UUID.randomUUID()).param("aggregate", workflowId)
            .param("payload", json(new PublishedWorkflow(workflowId, versionId, version, validation.topologicalOrder()))).update();
        return new PublishedWorkflow(workflowId, versionId, version, validation.topologicalOrder());
    }

    private void requireWorkspaceMember(UUID workspaceId, UUID actorId) {
        boolean allowed = jdbc.sql("SELECT EXISTS(SELECT 1 FROM workspace_members WHERE workspace_id=:workspace AND user_id=:actor AND status='ACTIVE')")
            .param("workspace", workspaceId).param("actor", actorId).query(Boolean.class).single();
        if (!allowed) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }

    private void requireWorkspaceEditor(UUID workspaceId, UUID actorId) {
        boolean allowed = jdbc.sql("SELECT EXISTS(SELECT 1 FROM workspace_members WHERE workspace_id=:workspace AND user_id=:actor AND status='ACTIVE' AND role IN ('OWNER','EDITOR'))")
            .param("workspace", workspaceId).param("actor", actorId).query(Boolean.class).single();
        if (!allowed) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalArgumentException("Invalid workflow JSON", e); }
    }

    private WorkflowGraph readGraph(String value) {
        try { return objectMapper.readValue(value, WorkflowGraph.class); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Stored workflow graph is invalid", e); }
    }

    public record CreateWorkflow(@NotBlank String name, String description, @NotNull @Valid WorkflowGraph graph) {}
    public record UpdateWorkflow(@NotBlank String name, String description, @NotNull @Valid WorkflowGraph graph, long lockVersion) {}
    public record WorkflowView(UUID id, UUID workspaceId, String name, String description, WorkflowGraph graph, Integer publishedVersion, long lockVersion, Instant updatedAt) {}
    public record WorkflowSummary(UUID id, String name, String description, Integer publishedVersion, long lockVersion, Instant updatedAt) {}
    public record PublishedWorkflow(UUID workflowId, UUID versionId, int version, java.util.List<String> topologicalOrder) {}
}

