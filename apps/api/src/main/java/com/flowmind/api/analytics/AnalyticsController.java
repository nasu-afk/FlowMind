package com.flowmind.api.analytics;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/analytics")
public class AnalyticsController {
    private final JdbcClient jdbc;
    public AnalyticsController(JdbcClient jdbc){this.jdbc=jdbc;}

    @GetMapping("/summary")
    public Summary summary(@PathVariable UUID workspaceId,@AuthenticationPrincipal Jwt jwt){
        requireMember(workspaceId,UUID.fromString(jwt.getSubject()));
        return jdbc.sql("SELECT COUNT(*) total,COUNT(*) FILTER(WHERE state='COMPLETED') completed,COUNT(*) FILTER(WHERE state='FAILED') failed,COALESCE(AVG(EXTRACT(EPOCH FROM (finished_at-started_at))*1000) FILTER(WHERE finished_at IS NOT NULL AND started_at IS NOT NULL),0) avg_ms FROM executions WHERE workspace_id=:w")
            .param("w",workspaceId).query((rs,row)->new Summary(rs.getLong("total"),rs.getLong("completed"),rs.getLong("failed"),rs.getBigDecimal("avg_ms"))).single();
    }
    @GetMapping("/daily")
    public List<Daily> daily(@PathVariable UUID workspaceId,@AuthenticationPrincipal Jwt jwt,@RequestParam(defaultValue="30") int days){
        requireMember(workspaceId,UUID.fromString(jwt.getSubject())); int bounded=Math.max(1,Math.min(days,365));
        return jdbc.sql("SELECT date_trunc('day',created_at) bucket,COUNT(*) total,COUNT(*) FILTER(WHERE state='COMPLETED') completed,COUNT(*) FILTER(WHERE state='FAILED') failed FROM executions WHERE workspace_id=:w AND created_at>=now()-(:days*interval '1 day') GROUP BY bucket ORDER BY bucket")
            .param("w",workspaceId).param("days",bounded).query((rs,row)->new Daily(rs.getTimestamp("bucket").toInstant(),rs.getLong("total"),rs.getLong("completed"),rs.getLong("failed"))).list();
    }
    private void requireMember(UUID workspaceId,UUID userId){boolean allowed=jdbc.sql("SELECT EXISTS(SELECT 1 FROM workspace_members WHERE workspace_id=:w AND user_id=:u AND status='ACTIVE')").param("w",workspaceId).param("u",userId).query(Boolean.class).single();if(!allowed)throw new ResponseStatusException(HttpStatus.FORBIDDEN);}
    public record Summary(long totalExecutions,long completed,long failed,BigDecimal averageDurationMs){}
    public record Daily(Instant day,long total,long completed,long failed){}
}

