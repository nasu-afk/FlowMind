package com.flowmind.api.trigger;

import org.springframework.scheduling.support.CronExpression;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

public class ScheduleCalculator {
    public Instant nextCron(String expression,String timezone,Instant after){
        var zone=ZoneId.of(timezone); var next=CronExpression.parse(expression).next(ZonedDateTime.ofInstant(after,zone));
        if(next==null)throw new IllegalArgumentException("Cron expression has no future occurrence"); return next.toInstant();
    }
    public int runsToDispatch(Instant scheduledFor,Instant now,MissedRunPolicy policy,int maxCatchUp){
        if(scheduledFor.isAfter(now))return 0;
        return switch(policy){case SKIP,RUN_ONCE->1;case CATCH_UP_BOUNDED->Math.max(1,Math.min(maxCatchUp,100));};
    }
    public DispatchPlan plan(String expression,String timezone,Instant scheduledFor,Instant now,MissedRunPolicy policy,int maxCatchUp){
        if(scheduledFor.isAfter(now))return new DispatchPlan(List.of(),scheduledFor);
        if(expression==null)return new DispatchPlan(List.of(scheduledFor),null);
        if(policy!=MissedRunPolicy.CATCH_UP_BOUNDED)return new DispatchPlan(List.of(scheduledFor),nextCron(expression,timezone,now));
        var runs=new ArrayList<Instant>();Instant cursor=scheduledFor;int limit=Math.max(1,Math.min(maxCatchUp,100));
        while(!cursor.isAfter(now)&&runs.size()<limit){runs.add(cursor);cursor=nextCron(expression,timezone,cursor);}
        return new DispatchPlan(List.copyOf(runs),cursor);
    }
    public record DispatchPlan(List<Instant> runs,Instant nextRunAt){}
    public enum MissedRunPolicy{SKIP,RUN_ONCE,CATCH_UP_BOUNDED}
}

