package com.flowmind.api.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.flowmind.api.workflow.WorkflowGraph;
import com.flowmind.api.workflow.WorkflowGraphValidator;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
@ConditionalOnProperty(name="flowmind.worker.enabled",havingValue="true",matchIfMissing=true)
public class WorkflowExecutionWorker {
    private final JdbcClient jdbc;
    private final ObjectMapper json;
    private final WorkflowGraphValidator validator;
    private final BranchEvaluator branches;
    private final AiOutputNormalizer aiOutputs;
    private final RestClient ai;

    public WorkflowExecutionWorker(JdbcClient jdbc, ObjectMapper json, WorkflowGraphValidator validator, BranchEvaluator branches, AiOutputNormalizer aiOutputs,
                                   @Value("${flowmind.ai-service-url}") String aiServiceUrl,
                                   @Value("${flowmind.ai-service-token}") String aiServiceToken) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(10_000);
        requestFactory.setReadTimeout(90_000);
        this.jdbc = jdbc; this.json = json; this.validator = validator; this.branches = branches; this.aiOutputs = aiOutputs;
        if (aiServiceToken == null || aiServiceToken.isBlank()) throw new IllegalStateException("AI service token is required");
        this.ai = RestClient.builder().requestFactory(requestFactory).baseUrl(aiServiceUrl)
            .defaultHeader("Authorization", "Bearer " + aiServiceToken).build();
    }

    @RabbitListener(queues = "flowmind.execution.queued")
    public void execute(String message) {
        UUID executionId = null;
        try {
            executionId = UUID.fromString(json.readTree(message).path("executionId").asText());
            var record = load(executionId);
            if (!claim(executionId)) return;
            var validation = validator.validate(record.graph());
            if (!validation.valid()) throw new IllegalStateException(String.join("; ", validation.errors()));
            var checkpoints = loadCheckpoints(executionId);
            var outputs = new HashMap<String, Object>();
            var skipped = new HashSet<String>();
            checkpoints.forEach((nodeId,checkpoint) -> {
                if ("COMPLETED".equals(checkpoint.status())) outputs.put(nodeId,checkpoint.output());
                if ("SKIPPED".equals(checkpoint.status())) skipped.add(nodeId);
            });
            for (String nodeId : validation.topologicalOrder()) {
                if (outputs.containsKey(nodeId) || skipped.contains(nodeId)) continue;
                var node = record.graph().nodes().stream().filter(candidate -> candidate.id().equals(nodeId)).findFirst().orElseThrow();
                if (!isActive(record.graph(), nodeId, outputs, skipped)) {
                    skipped.add(nodeId);
                    checkpoint(executionId, nodeId, "SKIPPED", Map.of("reason", "inactive branch"));
                    appendEvent(executionId, "node.skipped", Map.of("nodeId", nodeId));
                    continue;
                }
                if (isApproval(node)) {
                    Object approved = approvalOutput(executionId, node, record.workspaceId());
                    if (approved == null) return;
                    outputs.put(nodeId, approved);
                    checkpoint(executionId, nodeId, "COMPLETED", approved);
                    appendEvent(executionId, "node.completed", Map.of("nodeId", nodeId));
                    continue;
                }
                Object output = runNode(node, record.input(), outputs);
                outputs.put(nodeId, output);
                checkpoint(executionId, nodeId, "COMPLETED", output);
                appendEvent(executionId, "node.completed", Map.of("nodeId", nodeId));
            }
            jdbc.sql("UPDATE executions SET state='COMPLETED',output=CAST(:output AS jsonb),finished_at=now() WHERE id=:id AND state='RUNNING'")
                .param("output", write(Map.of("nodes", outputs, "skippedNodeIds", skipped))).param("id", executionId).update();
            appendEvent(executionId, "execution.completed", Map.of("completedNodeCount", outputs.size(), "skippedNodeCount", skipped.size()));
        } catch (Exception failure) {
            if (executionId != null) fail(executionId, failure);
        }
    }

    private boolean claim(UUID id) {
        int updated = jdbc.sql("UPDATE executions SET state='RUNNING',started_at=COALESCE(started_at,now()) WHERE id=:id AND state='QUEUED'").param("id", id).update();
        if (updated == 1) appendEvent(id, "execution.running", Map.of("worker", "rabbitmq"));
        return updated == 1;
    }

    private boolean isActive(WorkflowGraph graph, String nodeId, Map<String,Object> outputs, Set<String> skipped) {
        List<WorkflowGraph.Edge> incoming = graph.edges().stream().filter(edge -> edge.target().equals(nodeId)).toList();
        if (incoming.isEmpty()) return true;
        for (var edge : incoming) {
            if (skipped.contains(edge.source()) || !outputs.containsKey(edge.source())) continue;
            Object sourceOutput = outputs.get(edge.source());
            if (sourceOutput instanceof Map<?,?> map && map.containsKey("selectedBranch")) {
                if (edge.branch() == null || String.valueOf(map.get("selectedBranch")).equals(edge.branch())) return true;
            } else return true;
        }
        return false;
    }

    private Object runNode(WorkflowGraph.Node node, Map<String, Object> input, Map<String, Object> outputs) {
        if (isCondition(node)) {
            var decision = branches.evaluate(node, input, outputs);
            var result = new HashMap<String,Object>();
            result.put("selectedBranch", decision.selectedBranch());
            result.put("matched", decision.matched());
            result.put("actualValue", decision.actualValue());
            return result;
        }
        if (isTrigger(node)) return Map.of("input", input);
        if (!node.type().startsWith("ai.")) throw new UnsupportedOperationException("Node type " + node.type() + " has no runtime adapter");
        String model = String.valueOf(node.configuration().getOrDefault("model", "openai/gpt-oss-120b"));
        String instructions = String.valueOf(node.configuration().getOrDefault("instructions", "Return a concise JSON response."));
        var request = new HashMap<String,Object>();
        request.put("provider", "groq"); request.put("model", model);
        request.put("temperature", node.configuration().getOrDefault("temperature", 0.2));
        request.put("max_tokens", node.configuration().getOrDefault("maxTokens", 1024)); request.put("stream", false);
        request.put("messages", java.util.List.of(Map.of("role", "system", "content", instructions), Map.of("role", "user", "content", write(input))));
        Object responseSchema=node.configuration().get("responseSchema");
        if(responseSchema!=null)request.put("response_format",Map.of("type","json_schema","json_schema",Map.of("name","flowmind_node_output","strict",true,"schema",responseSchema)));
        String rawResponse = ai.post().uri("/v1/generate").contentType(MediaType.APPLICATION_JSON).body(write(request)).retrieve().body(String.class);
        JsonNode response;
        try { response = rawResponse == null ? null : json.readTree(rawResponse); }
        catch (Exception failure) { throw new IllegalStateException("AI service returned invalid JSON", failure); }
        if (response == null) throw new IllegalStateException("AI service returned an empty response");
        return aiOutputs.normalize(response, node.configuration());
    }

    private Record load(UUID id) throws Exception {
        return jdbc.sql("SELECT e.workspace_id,e.input::text,v.graph::text FROM executions e JOIN workflow_versions v ON v.id=e.workflow_version_id WHERE e.id=:id")
            .param("id", id).query((rs, row) -> {
                try { return new Record(rs.getObject(1,UUID.class), json.readValue(rs.getString(2), new TypeReference<>(){}), json.readValue(rs.getString(3), WorkflowGraph.class)); }
                catch (Exception failure) { throw new IllegalStateException(failure); }
            }).optional().orElseThrow();
    }

    private Object approvalOutput(UUID executionId, WorkflowGraph.Node node, UUID workspaceId) {
        var existing = jdbc.sql("SELECT id,status,decided_by,decided_at FROM approvals WHERE execution_id=:execution AND node_id=:node")
            .param("execution",executionId).param("node",node.id())
            .query((rs,row)->new Approval(rs.getObject(1,UUID.class),rs.getString(2),(UUID)rs.getObject(3),rs.getTimestamp(4)==null?null:rs.getTimestamp(4).toInstant())).optional();
        if (existing.isPresent() && "APPROVED".equals(existing.get().status())) {
            var output = new HashMap<String,Object>(); output.put("approvalId",existing.get().id()); output.put("approved",true); output.put("decidedBy",existing.get().decidedBy()); output.put("decidedAt",existing.get().decidedAt()); return output;
        }
        if (existing.isPresent() && "EXPIRED".equals(existing.get().status()) && "CONTINUE".equals(String.valueOf(node.configuration().getOrDefault("expirationPolicy","FAIL")))) {
            return Map.of("approvalId",existing.get().id(),"approved",false,"continuedAfterExpiry",true);
        }
        if (existing.isPresent() && !"PENDING".equals(existing.get().status())) throw new IllegalStateException("Approval ended with status "+existing.get().status());
        UUID approvalId = existing.map(Approval::id).orElseGet(UUID::randomUUID);
        if (existing.isEmpty()) {
            UUID assignedUser = parseUuid(node.configuration().get("assignedUserId"));
            String role = assignedUser==null?String.valueOf(node.configuration().getOrDefault("assignedRole","OWNER")):null;
            long expirationSeconds = longValue(node.configuration().getOrDefault("expirationSeconds",86400),86400);
            String policy = String.valueOf(node.configuration().getOrDefault("expirationPolicy","FAIL"));
            jdbc.sql("INSERT INTO approvals(id,execution_id,node_id,assigned_user_id,assigned_role,status,expires_at,expiration_policy) VALUES (:id,:execution,:node,:user,:role,'PENDING',now()+(:seconds * interval '1 second'),:policy) ON CONFLICT(execution_id,node_id) DO NOTHING")
                .param("id",approvalId).param("execution",executionId).param("node",node.id()).param("user",assignedUser).param("role",role).param("seconds",Math.max(60,expirationSeconds)).param("policy",policy).update();
            String notificationSql="INSERT INTO notifications(id,user_id,workspace_id,type,title,body,action_url) SELECT gen_random_uuid(),wm.user_id,:workspace,'APPROVAL_REQUEST','Workflow approval requested',:body,'/approvals' FROM workspace_members wm WHERE wm.workspace_id=:workspace AND wm.status='ACTIVE' AND "+(assignedUser==null?"wm.role=:assignee":"wm.user_id=:assignee");
            jdbc.sql(notificationSql).param("workspace",workspaceId).param("body","Execution "+executionId+" is waiting at "+node.id()).param("assignee",assignedUser==null?role:assignedUser).update();
        }
        checkpoint(executionId,node.id(),"WAITING",Map.of("approvalId",approvalId));
        jdbc.sql("UPDATE executions SET state='WAITING' WHERE id=:id AND state='RUNNING'").param("id",executionId).update();
        appendEvent(executionId,"approval.requested",Map.of("approvalId",approvalId,"nodeId",node.id()));
        return null;
    }

    private Map<String,Checkpoint> loadCheckpoints(UUID executionId) {
        var result = new HashMap<String,Checkpoint>();
        jdbc.sql("SELECT node_id,state::text FROM execution_checkpoints WHERE execution_id=:id ORDER BY attempt").param("id",executionId)
            .query((rs,row)->new String[]{rs.getString(1),rs.getString(2)}).list().forEach(item->{
                try { JsonNode state=json.readTree(item[1]); result.put(item[0],new Checkpoint(state.path("status").asText(),state.has("output")?json.convertValue(state.get("output"),Object.class):null)); }
                catch(Exception failure){throw new IllegalStateException(failure);}
            });
        return result;
    }

    private void checkpoint(UUID executionId,String nodeId,String status,Object output) {
        jdbc.sql("INSERT INTO execution_checkpoints(execution_id,node_id,attempt,state) VALUES (:execution,:node,1,CAST(:state AS jsonb)) ON CONFLICT(execution_id,node_id,attempt) DO UPDATE SET state=EXCLUDED.state,created_at=now()")
            .param("execution",executionId).param("node",nodeId).param("state",write(Map.of("status",status,"output",output))).update();
    }

    private boolean isCondition(WorkflowGraph.Node node) { return Set.of("if","switch","logic.condition").contains(node.type()); }
    private boolean isApproval(WorkflowGraph.Node node) { return Set.of("human.approval","approval.human").contains(node.type()); }
    private boolean isTrigger(WorkflowGraph.Node node) { return Set.of("manual","webhook","trigger.webhook","schedule","form").contains(node.type()); }
    private UUID parseUuid(Object value) { try{return value==null?null:UUID.fromString(String.valueOf(value));}catch(IllegalArgumentException invalid){throw new IllegalArgumentException("assignedUserId must be a UUID");} }
    private long longValue(Object value,long fallback) { try{return Long.parseLong(String.valueOf(value));}catch(RuntimeException invalid){return fallback;} }

    private void fail(UUID id, Exception failure) {
        String message = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        if (message.length() > 1000) message = message.substring(0, 1000);
        jdbc.sql("UPDATE executions SET state='FAILED',error=CAST(:error AS jsonb),finished_at=now() WHERE id=:id AND state NOT IN ('COMPLETED','FAILED','CANCELLED','TIMED_OUT')")
            .param("error", write(Map.of("message", message))).param("id", id).update();
        appendEvent(id, "execution.failed", Map.of("message", message));
    }

    private void appendEvent(UUID id, String type, Map<String, Object> payload) {
        jdbc.sql("INSERT INTO execution_events(execution_id,sequence_no,event_type,payload) SELECT :id,COALESCE(MAX(sequence_no),0)+1,:type,CAST(:payload AS jsonb) FROM execution_events WHERE execution_id=:id")
            .param("id", id).param("type", type).param("payload", write(payload)).update();
    }

    private String write(Object value) {
        try { return json.writeValueAsString(value); } catch (Exception failure) { throw new IllegalArgumentException(failure); }
    }

    private record Record(UUID workspaceId, Map<String, Object> input, WorkflowGraph graph) {}
    private record Checkpoint(String status,Object output) {}
    private record Approval(UUID id,String status,UUID decidedBy,java.time.Instant decidedAt) {}
}
