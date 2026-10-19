package com.flowmind.api.execution;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Component
@ConditionalOnProperty(name="flowmind.control-plane.enabled", havingValue="true", matchIfMissing=true)
public class OutboxRelay {
    private final JdbcClient jdbc;
    private final RabbitTemplate rabbit;
    private final String exchange;
    public OutboxRelay(JdbcClient jdbc, RabbitTemplate rabbit, @Value("${flowmind.outbox.exchange}") String exchange) {
        this.jdbc = jdbc; this.rabbit = rabbit; this.exchange = exchange;
    }

    @Scheduled(fixedDelayString = "${flowmind.outbox.poll-delay-ms:500}")
    @Transactional
    public void publishBatch() {
        List<Event> events = jdbc.sql("SELECT id,event_type,payload::text FROM outbox_events WHERE published_at IS NULL AND attempts < 20 ORDER BY created_at LIMIT 100 FOR UPDATE SKIP LOCKED")
            .query((rs,row) -> new Event(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3))).list();
        for (var event : events) {
            try {
                rabbit.convertAndSend(exchange, event.type(), event.payload(), message -> {
                    message.getMessageProperties().setMessageId(event.id().toString());
                    message.getMessageProperties().setContentType("application/json");
                    return message;
                });
                jdbc.sql("UPDATE outbox_events SET published_at=now(), attempts=attempts+1 WHERE id=:id").param("id",event.id()).update();
            } catch (RuntimeException failure) {
                jdbc.sql("UPDATE outbox_events SET attempts=attempts+1,last_error=:error WHERE id=:id")
                    .param("id",event.id()).param("error", truncate(failure.getMessage())).update();
            }
        }
    }
    private String truncate(String value) { return value == null ? "unknown" : value.substring(0, Math.min(value.length(), 1000)); }
    record Event(UUID id, String type, String payload) {}
}

