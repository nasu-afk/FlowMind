package com.flowmind.api.identity;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.Set;

@RestController
@RequestMapping("/api/organizations/{organizationId}/api-keys")
public class ApiKeyController {
    private static final Set<String> ALLOWED_SCOPES=Set.of("read","write","admin");
    private final JdbcClient jdbc;private final SecureRandom random=new SecureRandom();public ApiKeyController(JdbcClient jdbc){this.jdbc=jdbc;}
    @PostMapping @ResponseStatus(HttpStatus.CREATED) @Transactional public Created create(@PathVariable UUID organizationId,@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody Request request){UUID actor=UUID.fromString(jwt.getSubject());requireAdmin(organizationId,actor);if(request.scopes().isEmpty()||!ALLOWED_SCOPES.containsAll(request.scopes()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Scopes must contain read, write, or admin");byte[] bytes=new byte[32];random.nextBytes(bytes);String secret=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);String prefix="fm_"+UUID.randomUUID().toString().replace("-","").substring(0,12);UUID id=UUID.randomUUID();jdbc.sql("INSERT INTO api_keys(id,organization_id,name,key_prefix,secret_hash,scopes,created_by,expires_at) VALUES (:id,:organization,:name,:prefix,:hash,:scopes,:actor,:expires)").param("id",id).param("organization",organizationId).param("name",request.name()).param("prefix",prefix).param("hash",sha256(secret)).param("scopes",request.scopes().toArray(String[]::new)).param("actor",actor).param("expires",request.expiresAt()==null?null:Timestamp.from(request.expiresAt())).update();jdbc.sql("INSERT INTO audit_events(organization_id,actor_id,action,target_type,target_id) VALUES (:organization,:actor,'api_key.created','api_key',:target)").param("organization",organizationId).param("actor",actor).param("target",id.toString()).update();return new Created(id,request.name(),prefix+"."+secret,request.scopes(),request.expiresAt());}
    @GetMapping public List<View> list(@PathVariable UUID organizationId,@AuthenticationPrincipal Jwt jwt){requireAdmin(organizationId,UUID.fromString(jwt.getSubject()));return jdbc.sql("SELECT id,name,key_prefix,scopes,created_at,last_used_at,expires_at,revoked_at FROM api_keys WHERE organization_id=:organization ORDER BY created_at DESC").param("organization",organizationId).query((rs,row)->new View(rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3),(String[])rs.getArray(4).getArray(),rs.getTimestamp(5).toInstant(),instant(rs.getTimestamp(6)),instant(rs.getTimestamp(7)),instant(rs.getTimestamp(8)))).list();}
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void revoke(@PathVariable UUID organizationId,@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt){UUID actor=UUID.fromString(jwt.getSubject());requireAdmin(organizationId,actor);int changed=jdbc.sql("UPDATE api_keys SET revoked_at=COALESCE(revoked_at,now()) WHERE id=:id AND organization_id=:organization").param("id",id).param("organization",organizationId).update();if(changed==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND);}
    private void requireAdmin(UUID organization,UUID user){boolean allowed=jdbc.sql("SELECT EXISTS(SELECT 1 FROM organization_members WHERE organization_id=:o AND user_id=:u AND status='ACTIVE' AND role IN ('OWNER','ADMIN'))").param("o",organization).param("u",user).query(Boolean.class).single();if(!allowed)throw new ResponseStatusException(HttpStatus.FORBIDDEN);}
    private byte[] sha256(String value){try{return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));}catch(Exception impossible){throw new IllegalStateException(impossible);}}
    private Instant instant(java.sql.Timestamp value){return value==null?null:value.toInstant();}
    public record Request(@NotBlank @Size(max=120) String name,List<String> scopes,Instant expiresAt){public Request{scopes=scopes==null?List.of():List.copyOf(scopes);}}
    public record Created(UUID id,String name,String key,List<String> scopes,Instant expiresAt){}
    public record View(UUID id,String name,String keyPrefix,String[] scopes,Instant createdAt,Instant lastUsedAt,Instant expiresAt,Instant revokedAt){}
}

