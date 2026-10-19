package com.flowmind.api.trigger;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
@ConditionalOnProperty(name="flowmind.control-plane.enabled", havingValue="true", matchIfMissing=true)
public class DurableSchedulePoller {
    private final JdbcClient jdbc; private final TriggerExecutionService executions; private final ScheduleCalculator calculator=new ScheduleCalculator();
    public DurableSchedulePoller(JdbcClient jdbc,TriggerExecutionService executions){this.jdbc=jdbc;this.executions=executions;}
    @Scheduled(fixedDelayString="${flowmind.scheduler.poll-delay-ms:1000}")
    @Transactional
    public void poll(){
        List<Due> due=jdbc.sql("SELECT s.id,s.trigger_id,s.cron_expression,s.timezone,s.missed_run_policy,s.max_catch_up,s.next_run_at FROM schedules s JOIN triggers t ON t.id=s.trigger_id WHERE t.status='ACTIVE' AND s.next_run_at<=now() ORDER BY s.next_run_at LIMIT 100 FOR UPDATE OF s SKIP LOCKED")
            .query((rs,row)->new Due(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getString(3),rs.getString(4),ScheduleCalculator.MissedRunPolicy.valueOf(rs.getString(5)),rs.getInt(6),rs.getTimestamp(7).toInstant())).list();
        Instant now=Instant.now();
        for(var schedule:due){
            var plan=calculator.plan(schedule.cron(),schedule.timezone(),schedule.nextRunAt(),now,schedule.policy(),schedule.maxCatchUp());
            Instant last=null;
            for(Instant scheduledFor:plan.runs()){
                String key=schedule.id()+":"+scheduledFor; UUID deliveryId=UUID.randomUUID();
                int inserted=jdbc.sql("INSERT INTO trigger_deliveries(id,trigger_id,deduplication_key,status,request_metadata) VALUES (:id,:trigger,:key,'QUEUED',jsonb_build_object('scheduledFor',:scheduled)) ON CONFLICT(trigger_id,deduplication_key) DO NOTHING")
                    .param("id",deliveryId).param("trigger",schedule.triggerId()).param("key",key).param("scheduled",scheduledFor.toString()).update();
                if(inserted==1)executions.dispatch(schedule.triggerId(),deliveryId,java.util.Map.of("scheduledFor",scheduledFor.toString()));
                last=scheduledFor;
            }
            var update=jdbc.sql(plan.nextRunAt()==null
                    ? "UPDATE schedules SET last_run_at=COALESCE(:last,last_run_at),next_run_at=NULL WHERE id=:id"
                    : "UPDATE schedules SET last_run_at=COALESCE(:last,last_run_at),next_run_at=:next WHERE id=:id")
                .param("last",last==null?null:Timestamp.from(last)).param("id",schedule.id());
            if(plan.nextRunAt()!=null)update.param("next",Timestamp.from(plan.nextRunAt()));
            update.update();
        }
    }
    record Due(UUID id,UUID triggerId,String cron,String timezone,ScheduleCalculator.MissedRunPolicy policy,int maxCatchUp,Instant nextRunAt){}
}

