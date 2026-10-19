package com.flowmind.api.credential;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CredentialService {
    private final JdbcClient jdbc; private final ObjectMapper json; private final String masterKey;
    public CredentialService(JdbcClient jdbc,ObjectMapper json,@Value("${FLOWMIND_ENCRYPTION_KEY:}") String masterKey){this.jdbc=jdbc;this.json=json;this.masterKey=masterKey;}
    @Transactional public CredentialView create(UUID workspaceId,UUID actorId,CreateCredential request){requireEditor(workspaceId,actorId);if(masterKey.isBlank())throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Credential vault is awaiting KMS/master-key configuration");try{var crypto=new EnvelopeCrypto(masterKey,"env-v1");var encrypted=crypto.encrypt(json.writeValueAsBytes(request.secret()),workspaceId.toString());UUID id=UUID.randomUUID();jdbc.sql("INSERT INTO credentials(id,workspace_id,provider,name,encrypted_data,encrypted_data_key,key_version,status,created_by) VALUES (:id,:w,:provider,:name,:data,:key,:version,'ACTIVE',:actor)").param("id",id).param("w",workspaceId).param("provider",request.provider()).param("name",request.name()).param("data",encrypted.ciphertext()).param("key",encrypted.encryptedDataKey()).param("version",encrypted.keyVersion()).param("actor",actorId).update();audit(workspaceId,actorId,"credential.created",id);return new CredentialView(id,request.provider(),request.name(),"ACTIVE",Instant.now(),null);}catch(ResponseStatusException e){throw e;}catch(Exception e){throw new IllegalArgumentException("Credential payload cannot be encrypted",e);}}
    public List<CredentialView> list(UUID workspaceId,UUID actorId){requireMember(workspaceId,actorId);return jdbc.sql("SELECT id,provider,name,status,created_at,revoked_at FROM credentials WHERE workspace_id=:w ORDER BY created_at DESC").param("w",workspaceId).query((rs,row)->new CredentialView(rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3),rs.getString(4),rs.getTimestamp(5).toInstant(),rs.getTimestamp(6)==null?null:rs.getTimestamp(6).toInstant())).list();}
    @Transactional public void revoke(UUID workspaceId,UUID credentialId,UUID actorId){requireEditor(workspaceId,actorId);int changed=jdbc.sql("UPDATE credentials SET status='REVOKED',revoked_at=now() WHERE id=:id AND workspace_id=:w AND status<>'REVOKED'").param("id",credentialId).param("w",workspaceId).update();if(changed==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND);audit(workspaceId,actorId,"credential.revoked",credentialId);}
    private void requireMember(UUID w,UUID u){if(!jdbc.sql("SELECT EXISTS(SELECT 1 FROM workspace_members WHERE workspace_id=:w AND user_id=:u AND status='ACTIVE')").param("w",w).param("u",u).query(Boolean.class).single())throw new ResponseStatusException(HttpStatus.FORBIDDEN);}
    private void requireEditor(UUID w,UUID u){if(!jdbc.sql("SELECT EXISTS(SELECT 1 FROM workspace_members WHERE workspace_id=:w AND user_id=:u AND status='ACTIVE' AND role IN ('OWNER','EDITOR'))").param("w",w).param("u",u).query(Boolean.class).single())throw new ResponseStatusException(HttpStatus.FORBIDDEN);}
    private void audit(UUID w,UUID actor,String action,UUID target){jdbc.sql("INSERT INTO audit_events(workspace_id,actor_id,action,target_type,target_id) VALUES (:w,:actor,:action,'credential',:target)").param("w",w).param("actor",actor).param("action",action).param("target",target.toString()).update();}
    public record CreateCredential(String provider,String name,Map<String,String> secret){public CreateCredential{secret=secret==null?Map.of():Map.copyOf(secret);}}
    public record CredentialView(UUID id,String provider,String name,String status,Instant createdAt,Instant revokedAt){}
}

