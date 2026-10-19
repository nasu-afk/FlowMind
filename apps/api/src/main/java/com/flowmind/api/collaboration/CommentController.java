package com.flowmind.api.collaboration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/workflows/{workflowId}/comments")
public class CommentController {
    private final JdbcClient jdbc; public CommentController(JdbcClient jdbc){this.jdbc=jdbc;}
    @GetMapping public List<CommentView> list(@PathVariable UUID workspaceId,@PathVariable UUID workflowId,@AuthenticationPrincipal Jwt jwt){requireMember(workspaceId,workflowId,UUID.fromString(jwt.getSubject()),false);return jdbc.sql("SELECT c.id,c.author_id,c.parent_id,c.node_id,c.body,c.created_at,c.updated_at,c.resolved_at FROM workflow_comments c JOIN workflows w ON w.id=c.workflow_id WHERE c.workflow_id=:workflow AND w.workspace_id=:workspace ORDER BY c.created_at").param("workflow",workflowId).param("workspace",workspaceId).query((rs,row)->new CommentView(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),(UUID)rs.getObject(3),rs.getString(4),rs.getString(5),rs.getTimestamp(6).toInstant(),rs.getTimestamp(7).toInstant(),rs.getTimestamp(8)==null?null:rs.getTimestamp(8).toInstant())).list();}
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public CommentView create(@PathVariable UUID workspaceId,@PathVariable UUID workflowId,@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody Create request){UUID actor=UUID.fromString(jwt.getSubject());requireMember(workspaceId,workflowId,actor,true);UUID id=UUID.randomUUID();jdbc.sql("INSERT INTO workflow_comments(id,workflow_id,author_id,parent_id,node_id,body) VALUES (:id,:workflow,:author,:parent,:node,:body)").param("id",id).param("workflow",workflowId).param("author",actor).param("parent",request.parentId()).param("node",request.nodeId()).param("body",request.body()).update();return new CommentView(id,actor,request.parentId(),request.nodeId(),request.body(),Instant.now(),Instant.now(),null);}
    private void requireMember(UUID workspace,UUID workflow,UUID user,boolean edit){String roles=edit?"AND wm.role IN ('OWNER','EDITOR','OPERATOR')":"";boolean allowed=jdbc.sql("SELECT EXISTS(SELECT 1 FROM workflows w JOIN workspace_members wm ON wm.workspace_id=w.workspace_id WHERE w.id=:workflow AND w.workspace_id=:workspace AND wm.user_id=:user AND wm.status='ACTIVE' "+roles+")").param("workflow",workflow).param("workspace",workspace).param("user",user).query(Boolean.class).single();if(!allowed)throw new ResponseStatusException(HttpStatus.FORBIDDEN);}
    public record Create(UUID parentId,@Size(max=120) String nodeId,@NotBlank @Size(max=5000) String body){}
    public record CommentView(UUID id,UUID authorId,UUID parentId,String nodeId,String body,Instant createdAt,Instant updatedAt,Instant resolvedAt){}
}

