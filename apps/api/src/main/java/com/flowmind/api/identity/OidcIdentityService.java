package com.flowmind.api.identity;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

@Service
public class OidcIdentityService {
    private final JdbcClient jdbc; private final AuthService auth; private final boolean autoLinkVerifiedEmail; private final SecureRandom random=new SecureRandom();
    public OidcIdentityService(JdbcClient jdbc,AuthService auth,@Value("${flowmind.oidc.auto-link-verified-email:false}") boolean autoLinkVerifiedEmail){this.jdbc=jdbc;this.auth=auth;this.autoLinkVerifiedEmail=autoLinkVerifiedEmail;}

    @Transactional
    public String complete(String issuer,String subject,String email,String displayName,boolean emailVerified) {
        if(issuer==null||issuer.isBlank()||subject==null||subject.isBlank())throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"OIDC identity is incomplete");
        var existing=jdbc.sql("SELECT oi.user_id,om.organization_id,w.id FROM oidc_identities oi JOIN organization_members om ON om.user_id=oi.user_id AND om.status='ACTIVE' JOIN workspaces w ON w.organization_id=om.organization_id JOIN workspace_members wm ON wm.workspace_id=w.id AND wm.user_id=oi.user_id AND wm.status='ACTIVE' WHERE oi.issuer=:issuer AND oi.subject=:subject ORDER BY w.created_at LIMIT 1")
            .param("issuer",issuer).param("subject",subject).query((rs,row)->new Account(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getObject(3,UUID.class))).optional();
        Account account;
        if(existing.isPresent()){
            account=existing.get();jdbc.sql("UPDATE oidc_identities SET last_login_at=now() WHERE issuer=:issuer AND subject=:subject").param("issuer",issuer).param("subject",subject).update();
        }else{
            if(!emailVerified||email==null||email.isBlank())throw new ResponseStatusException(HttpStatus.FORBIDDEN,"A verified email claim is required");
            String normalized=email.trim().toLowerCase(Locale.ROOT);
            var user=jdbc.sql("SELECT id FROM users WHERE email=:email AND status='ACTIVE'").param("email",normalized).query(UUID.class).optional();
            if(user.isPresent()&&!autoLinkVerifiedEmail)throw new ResponseStatusException(HttpStatus.CONFLICT,"An account already exists; an administrator must link this SSO identity");
            UUID userId=user.orElseGet(UUID::randomUUID);
            if(user.isEmpty())jdbc.sql("INSERT INTO users(id,email,display_name,status) VALUES (:id,:email,:name,'ACTIVE')").param("id",userId).param("email",normalized).param("name",safeName(displayName,normalized)).update();
            account=membershipOrProvision(userId,normalized);
            try{jdbc.sql("INSERT INTO oidc_identities(issuer,subject,user_id,email_at_link) VALUES (:issuer,:subject,:user,:email)").param("issuer",issuer).param("subject",subject).param("user",userId).param("email",normalized).update();}
            catch(DuplicateKeyException race){throw new ResponseStatusException(HttpStatus.CONFLICT,"OIDC identity link changed; retry sign-in");}
            audit(account,"oidc.identity.linked",issuer);
        }
        byte[] codeBytes=new byte[32];random.nextBytes(codeBytes);String code=Base64.getUrlEncoder().withoutPadding().encodeToString(codeBytes);
        jdbc.sql("INSERT INTO oidc_login_codes(code_hash,user_id,organization_id,workspace_id,expires_at) VALUES (:hash,:user,:organization,:workspace,:expires)")
            .param("hash",hash(code)).param("user",account.userId()).param("organization",account.organizationId()).param("workspace",account.workspaceId()).param("expires",Timestamp.from(Instant.now().plus(Duration.ofMinutes(2)))).update();
        return code;
    }

    @Transactional
    public AuthService.Tokens exchange(String code,String userAgent) {
        var login=jdbc.sql("SELECT user_id,organization_id,workspace_id FROM oidc_login_codes WHERE code_hash=:hash AND used_at IS NULL AND expires_at>now() FOR UPDATE")
            .param("hash",hash(code)).query((rs,row)->new Account(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getObject(3,UUID.class))).optional()
            .orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED,"OIDC login code is invalid or expired"));
        jdbc.sql("UPDATE oidc_login_codes SET used_at=now() WHERE code_hash=:hash").param("hash",hash(code)).update();
        audit(login,"oidc.login","exchange");
        return auth.issueForExternalIdentity(login.userId(),login.organizationId(),login.workspaceId(),userAgent);
    }

    private Account membershipOrProvision(UUID userId,String email){
        var current=jdbc.sql("SELECT om.organization_id,w.id FROM organization_members om JOIN workspaces w ON w.organization_id=om.organization_id JOIN workspace_members wm ON wm.workspace_id=w.id AND wm.user_id=om.user_id WHERE om.user_id=:user AND om.status='ACTIVE' AND wm.status='ACTIVE' ORDER BY w.created_at LIMIT 1").param("user",userId).query((rs,row)->new Account(userId,rs.getObject(1,UUID.class),rs.getObject(2,UUID.class))).optional();
        if(current.isPresent())return current.get();
        UUID organizationId=UUID.randomUUID(),workspaceId=UUID.randomUUID();String domain=email.substring(email.indexOf('@')+1);String slug=domain.replaceAll("[^a-z0-9]+","-")+"-"+userId.toString().substring(0,8);
        jdbc.sql("INSERT INTO organizations(id,name,slug) VALUES (:id,:name,:slug)").param("id",organizationId).param("name",domain+" workspace").param("slug",slug).update();
        jdbc.sql("INSERT INTO organization_members(organization_id,user_id,role,status) VALUES (:organization,:user,'OWNER','ACTIVE')").param("organization",organizationId).param("user",userId).update();
        jdbc.sql("INSERT INTO workspaces(id,organization_id,name,slug) VALUES (:id,:organization,'Main workspace','main')").param("id",workspaceId).param("organization",organizationId).update();
        jdbc.sql("INSERT INTO workspace_members(workspace_id,user_id,role,status) VALUES (:workspace,:user,'OWNER','ACTIVE')").param("workspace",workspaceId).param("user",userId).update();
        return new Account(userId,organizationId,workspaceId);
    }
    private String safeName(String name,String email){String value=name==null||name.isBlank()?email.substring(0,email.indexOf('@')):name.trim();return value.substring(0,Math.min(value.length(),120));}
    private byte[] hash(String code){try{return MessageDigest.getInstance("SHA-256").digest(code.getBytes(StandardCharsets.UTF_8));}catch(Exception failure){throw new IllegalStateException(failure);}}
    private void audit(Account account,String action,String issuer){jdbc.sql("INSERT INTO audit_events(organization_id,workspace_id,actor_id,action,target_type,target_id,metadata) VALUES (:organization,:workspace,:user,:action,'user',CAST(:user AS text),jsonb_build_object('issuer',:issuer))").param("organization",account.organizationId()).param("workspace",account.workspaceId()).param("user",account.userId()).param("action",action).param("issuer",issuer).update();}
    private record Account(UUID userId,UUID organizationId,UUID workspaceId){}
}
