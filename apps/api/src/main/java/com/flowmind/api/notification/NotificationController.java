package com.flowmind.api.notification;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final JdbcClient jdbc;
    public NotificationController(JdbcClient jdbc){this.jdbc=jdbc;}
    @GetMapping public List<NotificationView> list(@AuthenticationPrincipal Jwt jwt,@RequestParam(defaultValue="false") boolean unreadOnly){
        UUID userId=UUID.fromString(jwt.getSubject());
        String sql="SELECT id,type,title,body,action_url,read_at,created_at FROM notifications WHERE user_id=:user"+(unreadOnly?" AND read_at IS NULL":"")+" ORDER BY created_at DESC LIMIT 100";
        return jdbc.sql(sql).param("user",userId).query((rs,row)->new NotificationView(rs.getObject("id",UUID.class),rs.getString("type"),rs.getString("title"),rs.getString("body"),rs.getString("action_url"),rs.getTimestamp("read_at")==null?null:rs.getTimestamp("read_at").toInstant(),rs.getTimestamp("created_at").toInstant())).list();
    }
    @PostMapping("/{id}/read") public void markRead(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id){jdbc.sql("UPDATE notifications SET read_at=COALESCE(read_at,now()) WHERE id=:id AND user_id=:user").param("id",id).param("user",UUID.fromString(jwt.getSubject())).update();}
    public record NotificationView(UUID id,String type,String title,String body,String actionUrl,Instant readAt,Instant createdAt){}
}

