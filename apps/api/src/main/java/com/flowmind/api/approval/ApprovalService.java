package com.flowmind.api.approval;

import com.flowmind.api.execution.ExecutionState;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.time.Instant;
import java.util.UUID;

@Service
public class ApprovalService {
    private final JdbcClient jdbc;
    public ApprovalService(JdbcClient jdbc){this.jdbc=jdbc;}
    @Transactional
    public DecisionResult decide(UUID approvalId,UUID actorId,Decision decision,String comment){
        var approval=jdbc.sql("SELECT a.execution_id,a.status,a.assigned_user_id,a.assigned_role,e.workspace_id FROM approvals a JOIN executions e ON e.id=a.execution_id WHERE a.id=:id FOR UPDATE")
            .param("id",approvalId).query((rs,row)->new Pending(rs.getObject(1,UUID.class),rs.getString(2),(UUID)rs.getObject(3),rs.getString(4),rs.getObject(5,UUID.class))).optional()
            .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
        if(!"PENDING".equals(approval.status()))throw new ResponseStatusException(HttpStatus.CONFLICT,"Approval is no longer pending");
        boolean assigned=actorId.equals(approval.assignedUserId()) || (approval.assignedRole()!=null && jdbc.sql("SELECT EXISTS(SELECT 1 FROM workspace_members WHERE workspace_id=:w AND user_id=:u AND status='ACTIVE' AND role=:role)").param("w",approval.workspaceId()).param("u",actorId).param("role",approval.assignedRole()).query(Boolean.class).single());
        if(!assigned)throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        jdbc.sql("UPDATE approvals SET status=:status,decision_comment=:comment,decided_by=:actor,decided_at=now() WHERE id=:id AND status='PENDING'")
            .param("status",decision.name()).param("comment",comment).param("actor",actorId).param("id",approvalId).update();
        ExecutionState next=decision==Decision.APPROVED?ExecutionState.QUEUED:ExecutionState.FAILED;
        jdbc.sql("UPDATE executions SET state=:state,error=CASE WHEN :state='FAILED' THEN jsonb_build_object('message','Approval rejected') ELSE error END,finished_at=CASE WHEN :state='FAILED' THEN now() ELSE finished_at END WHERE id=:id AND state='WAITING'").param("state",next.name()).param("id",approval.executionId()).update();
        jdbc.sql("INSERT INTO execution_events(execution_id,sequence_no,event_type,payload) SELECT :execution,COALESCE(MAX(sequence_no),0)+1,:type,jsonb_build_object('approvalId',CAST(:approval AS text),'actorId',CAST(:actor AS text)) FROM execution_events WHERE execution_id=:execution")
            .param("execution",approval.executionId()).param("type",decision==Decision.APPROVED?"approval.approved":"approval.rejected").param("approval",approvalId).param("actor",actorId).update();
        if(decision==Decision.APPROVED) jdbc.sql("INSERT INTO outbox_events(id,aggregate_type,aggregate_id,event_type,event_version,payload) VALUES (:id,'execution',:execution,'execution.resumed',1,jsonb_build_object('executionId',CAST(:execution AS text)))").param("id",UUID.randomUUID()).param("execution",approval.executionId()).update();
        jdbc.sql("INSERT INTO audit_events(organization_id,workspace_id,actor_id,action,target_type,target_id,metadata) SELECT w.organization_id,:workspace,:actor,:action,'approval',:target,jsonb_build_object('decision',:decision) FROM workspaces w WHERE w.id=:workspace")
            .param("workspace",approval.workspaceId()).param("actor",actorId).param("action","approval."+decision.name().toLowerCase()).param("target",approvalId.toString()).param("decision",decision.name()).update();
        return new DecisionResult(approvalId,decision,next);
    }

    @Transactional(readOnly=true)
    public List<ApprovalView> list(UUID workspaceId,UUID actorId,String status){
        requireMember(workspaceId,actorId);
        String requestedStatus=status==null||status.isBlank()?"*":status.toUpperCase();
        if(!List.of("*","PENDING","APPROVED","REJECTED","EXPIRED").contains(requestedStatus))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unsupported approval status");
        String sql="SELECT a.id,a.execution_id,a.node_id,a.assigned_user_id,a.assigned_role,a.status,a.expires_at,a.created_at FROM approvals a JOIN executions e ON e.id=a.execution_id WHERE e.workspace_id=:workspace AND (:status='*' OR a.status=:status) AND (a.assigned_user_id=:actor OR (a.assigned_user_id IS NULL AND EXISTS(SELECT 1 FROM workspace_members wm WHERE wm.workspace_id=:workspace AND wm.user_id=:actor AND wm.status='ACTIVE' AND wm.role=a.assigned_role))) ORDER BY a.created_at DESC";
        return jdbc.sql(sql).param("workspace",workspaceId).param("actor",actorId).param("status",requestedStatus).query((rs,row)->new ApprovalView(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getString(3),(UUID)rs.getObject(4),rs.getString(5),rs.getString(6),instant(rs.getTimestamp(7)),rs.getTimestamp(8).toInstant())).list();
    }
    private void requireMember(UUID workspaceId,UUID actorId){boolean allowed=jdbc.sql("SELECT EXISTS(SELECT 1 FROM workspace_members WHERE workspace_id=:w AND user_id=:u AND status='ACTIVE')").param("w",workspaceId).param("u",actorId).query(Boolean.class).single();if(!allowed)throw new ResponseStatusException(HttpStatus.FORBIDDEN);}
    private Instant instant(java.sql.Timestamp value){return value==null?null:value.toInstant();}
    public enum Decision { APPROVED,REJECTED }
    public record DecisionResult(UUID approvalId,Decision decision,ExecutionState executionState){}
    public record ApprovalView(UUID id,UUID executionId,String nodeId,UUID assignedUserId,String assignedRole,String status,Instant expiresAt,Instant createdAt){}
    private record Pending(UUID executionId,String status,UUID assignedUserId,String assignedRole,UUID workspaceId){}
}

