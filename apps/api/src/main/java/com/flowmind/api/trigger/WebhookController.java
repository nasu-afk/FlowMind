package com.flowmind.api.trigger;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/webhooks")
public class WebhookController {
    private static final int MAX_BYTES=1_048_576;
    private final JdbcClient jdbc; private final ObjectMapper json; private final TriggerExecutionService executions; private final WebhookSignatureVerifier verifier=new WebhookSignatureVerifier(Clock.systemUTC(),Duration.ofMinutes(5));
    public WebhookController(JdbcClient jdbc,ObjectMapper json,TriggerExecutionService executions){this.jdbc=jdbc;this.json=json;this.executions=executions;}
    @PostMapping("/{triggerId}/{endpointToken}") @ResponseStatus(HttpStatus.ACCEPTED) @Transactional
    public Accepted ingest(@PathVariable UUID triggerId,@PathVariable String endpointToken,@RequestHeader("X-FlowMind-Timestamp") String timestamp,@RequestHeader("X-FlowMind-Signature") String signature,@RequestHeader(value="Idempotency-Key",required=false) String idempotencyKey,@RequestBody byte[] body,HttpServletRequest request){
        if(body.length>MAX_BYTES)throw new ResponseStatusException(HttpStatus.CONTENT_TOO_LARGE);
        byte[] stored=jdbc.sql("SELECT secret_hash FROM triggers WHERE id=:id AND type='webhook' AND status='ACTIVE'").param("id",triggerId).query(byte[].class).optional().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
        byte[] tokenBytes=endpointToken.getBytes(StandardCharsets.UTF_8);if(!MessageDigest.isEqual(stored,sha256(tokenBytes)))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid webhook endpoint token");
        if(!verifier.verify(body,timestamp,signature,tokenBytes))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid or expired webhook signature");
        String dedup=idempotencyKey==null||idempotencyKey.isBlank()?HexFormat.of().formatHex(sha256((timestamp+":"+HexFormat.of().formatHex(sha256(body))).getBytes(StandardCharsets.UTF_8))):idempotencyKey.substring(0,Math.min(200,idempotencyKey.length()));
        UUID delivery=UUID.randomUUID();int inserted=jdbc.sql("INSERT INTO trigger_deliveries(id,trigger_id,deduplication_key,request_metadata,status) VALUES (:id,:trigger,:dedup,CAST(:metadata AS jsonb),'QUEUED') ON CONFLICT(trigger_id,deduplication_key) DO NOTHING")
            .param("id",delivery).param("trigger",triggerId).param("dedup",dedup).param("metadata",write(Map.of("contentType",String.valueOf(request.getContentType()),"size",body.length,"payloadSha256",HexFormat.of().formatHex(sha256(body))))).update();
        if(inserted==0)return new Accepted(null,true);
        executions.dispatch(triggerId,delivery,webhookInput(body));
        return new Accepted(delivery,false);
    }
    private Map<String,Object> webhookInput(byte[] body){
        try{
            Object value=json.readValue(body,Object.class);
            if(value instanceof Map<?,?> map){var result=new java.util.HashMap<String,Object>();map.forEach((key,item)->result.put(String.valueOf(key),item));return result;}
            return Map.of("payload",value==null?"null":value);
        }catch(Exception invalid){return Map.of("body",new String(body,StandardCharsets.UTF_8));}
    }
    private byte[] sha256(byte[] input){try{return MessageDigest.getInstance("SHA-256").digest(input);}catch(Exception impossible){throw new IllegalStateException(impossible);}}
    private String write(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalArgumentException("Webhook payload metadata is invalid",e);}}
    public record Accepted(UUID deliveryId,boolean duplicate){}
}

