package com.flowmind.api.approval;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Component
@ConditionalOnProperty(name="flowmind.control-plane.enabled",havingValue="true",matchIfMissing=true)
public class ApprovalExpiryPoller {
    private final JdbcClient jdbc;
    public ApprovalExpiryPoller(JdbcClient jdbc){this.jdbc=jdbc;}

    @Scheduled(fixedDelayString="${flowmind.approvals.expiry-poll-delay-ms:30000}")
    @Transactional
    public void expire(){
        var due=jdbc.sql("SELECT id,execution_id,expiration_policy FROM approvals WHERE status='PENDING' AND expires_at<=now() ORDER BY expires_at LIMIT 100 FOR UPDATE SKIP LOCKED")
            .query((rs,row)->new Due(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getString(3))).list();
        for(var approval:due){
            jdbc.sql("UPDATE approvals SET status='EXPIRED',decided_at=now() WHERE id=:id AND status='PENDING'").param("id",approval.id()).update();
            if("CONTINUE".equals(approval.policy())){
                jdbc.sql("UPDATE executions SET state='QUEUED' WHERE id=:id AND state='WAITING'").param("id",approval.executionId()).update();
                jdbc.sql("INSERT INTO outbox_events(id,aggregate_type,aggregate_id,event_type,event_version,payload) VALUES (:id,'execution',:execution,'execution.resumed',1,jsonb_build_object('executionId',CAST(:execution AS text)))").param("id",UUID.randomUUID()).param("execution",approval.executionId()).update();
            }else jdbc.sql("UPDATE executions SET state='FAILED',error=jsonb_build_object('message','Approval expired'),finished_at=now() WHERE id=:id AND state='WAITING'").param("id",approval.executionId()).update();
            jdbc.sql("INSERT INTO execution_events(execution_id,sequence_no,event_type,payload) SELECT :execution,COALESCE(MAX(sequence_no),0)+1,'approval.expired',jsonb_build_object('approvalId',CAST(:approval AS text),'policy',:policy) FROM execution_events WHERE execution_id=:execution")
                .param("execution",approval.executionId()).param("approval",approval.id()).param("policy",approval.policy()).update();
        }
    }
    private record Due(UUID id,UUID executionId,String policy){}
}
