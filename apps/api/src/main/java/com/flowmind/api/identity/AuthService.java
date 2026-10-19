package com.flowmind.api.identity;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
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
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class AuthService {
    private final JdbcClient jdbc; private final PasswordEncoder passwords; private final JwtEncoder jwt; private final MfaService mfa;
    private final String issuer; private final String audience; private final SecureRandom random=new SecureRandom();
    public AuthService(JdbcClient jdbc,PasswordEncoder passwords,JwtEncoder jwt,MfaService mfa,@Value("${flowmind.security.issuer}") String issuer,@Value("${flowmind.security.audience}") String audience) {
        this.jdbc=jdbc; this.passwords=passwords; this.jwt=jwt; this.mfa=mfa; this.issuer=issuer; this.audience=audience;
    }

    @Transactional
    public Tokens register(Register request,String userAgent) {
        String email=normalizeEmail(request.email()); UUID userId=UUID.randomUUID(); UUID organizationId=UUID.randomUUID(); UUID workspaceId=UUID.randomUUID();
        try {
            jdbc.sql("INSERT INTO users(id,email,display_name,password_hash,status) VALUES (:id,:email,:name,:password,'ACTIVE')")
                .param("id",userId).param("email",email).param("name",request.displayName().trim()).param("password",passwords.encode(request.password())).update();
        } catch(DuplicateKeyException duplicate) { throw new ResponseStatusException(HttpStatus.CONFLICT,"An account with this email already exists"); }
        String slug=slug(request.organizationName())+"-"+userId.toString().substring(0,8);
        jdbc.sql("INSERT INTO organizations(id,name,slug) VALUES (:id,:name,:slug)").param("id",organizationId).param("name",request.organizationName().trim()).param("slug",slug).update();
        jdbc.sql("INSERT INTO organization_members(organization_id,user_id,role,status) VALUES (:o,:u,'OWNER','ACTIVE')").param("o",organizationId).param("u",userId).update();
        jdbc.sql("INSERT INTO workspaces(id,organization_id,name,slug) VALUES (:id,:o,'Main workspace','main')").param("id",workspaceId).param("o",organizationId).update();
        jdbc.sql("INSERT INTO workspace_members(workspace_id,user_id,role,status) VALUES (:w,:u,'OWNER','ACTIVE')").param("w",workspaceId).param("u",userId).update();
        return issue(userId,organizationId,workspaceId,userAgent);
    }

    @Transactional
    public Tokens login(Login request,String userAgent) {
        var account=jdbc.sql("SELECT u.id,u.password_hash,om.organization_id,w.id workspace_id,u.mfa_enabled FROM users u JOIN organization_members om ON om.user_id=u.id AND om.status='ACTIVE' JOIN workspaces w ON w.organization_id=om.organization_id JOIN workspace_members wm ON wm.workspace_id=w.id AND wm.user_id=u.id AND wm.status='ACTIVE' WHERE u.email=:email AND u.status='ACTIVE' ORDER BY w.created_at LIMIT 1")
            .param("email",normalizeEmail(request.email())).query((rs,row)->new Account(rs.getObject(1,UUID.class),rs.getString(2),rs.getObject(3,UUID.class),rs.getObject(4,UUID.class),rs.getBoolean(5))).optional();
        if (account.isEmpty() || account.get().passwordHash()==null || !passwords.matches(request.password(),account.get().passwordHash()))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid email or password");
        if(account.get().mfaEnabled()&&(request.mfaCode()==null||request.mfaCode().isBlank()))throw new ResponseStatusException(HttpStatus.PRECONDITION_REQUIRED,"MFA code required");
        if(account.get().mfaEnabled()&&!mfa.verifyLoginCode(account.get().userId(),request.mfaCode()))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid MFA code");
        return issue(account.get().userId(),account.get().organizationId(),account.get().workspaceId(),userAgent);
    }

    @Transactional
    public Tokens refresh(String refreshToken,String userAgent) {
        byte[] hash=sha256(refreshToken);
        var session=jdbc.sql("SELECT s.id,s.user_id,om.organization_id,w.id FROM auth_sessions s JOIN organization_members om ON om.user_id=s.user_id AND om.status='ACTIVE' JOIN workspaces w ON w.organization_id=om.organization_id JOIN workspace_members wm ON wm.workspace_id=w.id AND wm.user_id=s.user_id AND wm.status='ACTIVE' WHERE s.refresh_token_hash=:hash AND s.revoked_at IS NULL AND s.expires_at>now() ORDER BY w.created_at LIMIT 1 FOR UPDATE")
            .param("hash",hash).query((rs,row)->new Session(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getObject(3,UUID.class),rs.getObject(4,UUID.class))).optional()
            .orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Refresh token is invalid or expired"));
        jdbc.sql("UPDATE auth_sessions SET revoked_at=now(),rotated_at=now() WHERE id=:id").param("id",session.id()).update();
        return issue(session.userId(),session.organizationId(),session.workspaceId(),userAgent);
    }

    @Transactional
    public void logout(String refreshToken) { jdbc.sql("UPDATE auth_sessions SET revoked_at=COALESCE(revoked_at,now()) WHERE refresh_token_hash=:hash").param("hash",sha256(refreshToken)).update(); }

    @Transactional
    public Tokens issueForExternalIdentity(UUID userId,UUID organizationId,UUID workspaceId,String userAgent) { return issue(userId,organizationId,workspaceId,userAgent); }

    private Tokens issue(UUID userId,UUID organizationId,UUID workspaceId,String userAgent) {
        Instant now=Instant.now(); Instant expires=now.plus(Duration.ofMinutes(15));
        var claims=JwtClaimsSet.builder().issuer(issuer).issuedAt(now).expiresAt(expires).subject(userId.toString()).audience(List.of(audience))
            .claim("organization_id",organizationId.toString()).claim("workspace_id",workspaceId.toString()).build();
        String access=jwt.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),claims)).getTokenValue();
        byte[] refreshBytes=new byte[48]; random.nextBytes(refreshBytes); String refresh=Base64.getUrlEncoder().withoutPadding().encodeToString(refreshBytes);
        jdbc.sql("INSERT INTO auth_sessions(user_id,refresh_token_hash,user_agent,expires_at) VALUES (:u,:hash,:agent,:expires)")
            .param("u",userId).param("hash",sha256(refresh)).param("agent",truncate(userAgent,500)).param("expires",Timestamp.from(now.plus(Duration.ofDays(30)))).update();
        return new Tokens(access,refresh,expires,userId,organizationId,workspaceId);
    }
    private String normalizeEmail(String email){return email.trim().toLowerCase(Locale.ROOT);}
    private String slug(String value){String result=value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+","-").replaceAll("(^-|-$)","");return result.isBlank()?"organization":result;}
    private byte[] sha256(String value){try{return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));}catch(Exception impossible){throw new IllegalStateException(impossible);}}
    private String truncate(String value,int max){return value==null?null:value.substring(0,Math.min(max,value.length()));}
    public record Register(String email,String displayName,String password,String organizationName) {}
    public record Login(String email,String password,String mfaCode) {}
    public record Refresh(String refreshToken) {}
    public record Tokens(String accessToken,String refreshToken,Instant accessTokenExpiresAt,UUID userId,UUID organizationId,UUID workspaceId) {}
    private record Account(UUID userId,String passwordHash,UUID organizationId,UUID workspaceId,boolean mfaEnabled) {}
    private record Session(UUID id,UUID userId,UUID organizationId,UUID workspaceId) {}
}

