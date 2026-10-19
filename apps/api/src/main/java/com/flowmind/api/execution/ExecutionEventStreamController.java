package com.flowmind.api.execution;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/executions/{executionId}/events")
public class ExecutionEventStreamController {
    private final JdbcClient jdbc;
    public ExecutionEventStreamController(JdbcClient jdbc){this.jdbc=jdbc;}
    @GetMapping(produces="text/event-stream")
    public SseEmitter stream(@PathVariable UUID workspaceId,@PathVariable UUID executionId,@AuthenticationPrincipal Jwt jwt,@RequestHeader(value="Last-Event-ID",required=false) Long lastEventId){
        UUID user=UUID.fromString(jwt.getSubject());boolean allowed=jdbc.sql("SELECT EXISTS(SELECT 1 FROM executions e JOIN workspace_members wm ON wm.workspace_id=e.workspace_id WHERE e.id=:execution AND e.workspace_id=:workspace AND wm.user_id=:user AND wm.status='ACTIVE')").param("execution",executionId).param("workspace",workspaceId).param("user",user).query(Boolean.class).single();if(!allowed)throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        var emitter=new SseEmitter(Duration.ofMinutes(30).toMillis());var open=new AtomicBoolean(true);emitter.onCompletion(()->open.set(false));emitter.onTimeout(()->open.set(false));emitter.onError(error->open.set(false));
        Thread.ofVirtual().name("execution-events-"+executionId).start(()->pump(executionId,lastEventId==null?0:lastEventId,emitter,open));return emitter;
    }
    private void pump(UUID executionId,long cursor,SseEmitter emitter,AtomicBoolean open){long next=cursor;Instant deadline=Instant.now().plus(Duration.ofMinutes(30));try{while(open.get()&&Instant.now().isBefore(deadline)){List<Event> events=jdbc.sql("SELECT id,event_type,payload::text,created_at FROM execution_events WHERE execution_id=:execution AND id>:cursor ORDER BY id LIMIT 200").param("execution",executionId).param("cursor",next).query((rs,row)->new Event(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getTimestamp(4).toInstant())).list();for(var event:events){emitter.send(SseEmitter.event().id(Long.toString(event.id())).name(event.type()).data(event));next=event.id();}if(events.isEmpty()){emitter.send(SseEmitter.event().comment("keepalive"));Thread.sleep(1000);}}emitter.complete();}catch(InterruptedException interrupted){Thread.currentThread().interrupt();emitter.complete();}catch(Exception disconnected){emitter.completeWithError(disconnected);}}
    public record Event(long id,String type,String payload,Instant createdAt){}
}
