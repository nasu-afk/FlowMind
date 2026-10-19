package com.flowmind.api.execution;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
@ConditionalOnProperty(name="flowmind.control-plane.enabled",havingValue="true",matchIfMissing=true)
public class ExecutionRecoveryPoller {
    private final JdbcClient jdbc;
    private final long staleAfterSeconds;

    public ExecutionRecoveryPoller(JdbcClient jdbc,
                                   @Value("${flowmind.execution.stale-after-seconds:600}") long staleAfterSeconds) {
        this.jdbc = jdbc;
        this.staleAfterSeconds = Math.max(60, staleAfterSeconds);
    }

    @Scheduled(fixedDelayString="${flowmind.execution.recovery-poll-delay-ms:60000}")
    @Transactional
    public void recoverStaleExecutions() {
        var stale = jdbc.sql("SELECT id FROM executions WHERE state='RUNNING' AND started_at < now() - (:seconds * interval '1 second') ORDER BY started_at LIMIT 100 FOR UPDATE SKIP LOCKED")
            .param("seconds", staleAfterSeconds).query(UUID.class).list();
        for (UUID executionId : stale) {
            int recovered = jdbc.sql("UPDATE executions SET state='QUEUED',started_at=NULL,error=NULL WHERE id=:id AND state='RUNNING'")
                .param("id",executionId).update();
            if (recovered == 0) continue;
            jdbc.sql("INSERT INTO execution_events(execution_id,sequence_no,event_type,payload) SELECT :execution,COALESCE(MAX(sequence_no),0)+1,'execution.recovered',jsonb_build_object('reason','stale worker lease') FROM execution_events WHERE execution_id=:execution")
                .param("execution",executionId).update();
            jdbc.sql("INSERT INTO outbox_events(id,aggregate_type,aggregate_id,event_type,event_version,payload) VALUES (:id,'execution',:execution,'execution.resumed',1,jsonb_build_object('executionId',CAST(:execution AS text)))")
                .param("id",UUID.randomUUID()).param("execution",executionId).update();
        }
    }
}
