package com.flowmind.api.identity;

import com.flowmind.api.credential.EnvelopeCrypto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
public class MfaService {
    private final JdbcClient jdbc;
    private final TotpService totp;
    private final String masterKey;
    private final SecureRandom random=new SecureRandom();

    public MfaService(JdbcClient jdbc,TotpService totp,@Value("${FLOWMIND_ENCRYPTION_KEY:}") String masterKey){this.jdbc=jdbc;this.totp=totp;this.masterKey=masterKey;}

    @Transactional
    public Setup setup(UUID userId) {
        requireKey();
        boolean enabled=jdbc.sql("SELECT mfa_enabled FROM users WHERE id=:id AND status='ACTIVE'").param("id",userId).query(Boolean.class).optional().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
        if(enabled)throw new ResponseStatusException(HttpStatus.CONFLICT,"MFA is already enabled");
        String email=jdbc.sql("SELECT email FROM users WHERE id=:id").param("id",userId).query(String.class).single();
        String secret=totp.generateSecret();
        var encrypted=crypto().encrypt(secret.getBytes(StandardCharsets.UTF_8),context(userId));
        jdbc.sql("INSERT INTO user_mfa_totp(user_id,encrypted_secret,encrypted_data_key,key_version) VALUES (:user,:secret,:key,:version) ON CONFLICT(user_id) DO UPDATE SET encrypted_secret=EXCLUDED.encrypted_secret,encrypted_data_key=EXCLUDED.encrypted_data_key,key_version=EXCLUDED.key_version,confirmed_at=NULL,updated_at=now()")
            .param("user",userId).param("secret",encrypted.ciphertext()).param("key",encrypted.encryptedDataKey()).param("version",encrypted.keyVersion()).update();
        String uri="otpauth://totp/FlowMind:"+encode(email)+"?secret="+secret+"&issuer=FlowMind&algorithm=SHA1&digits=6&period=30";
        return new Setup(secret,uri);
    }

    @Transactional
    public RecoveryCodes confirm(UUID userId,String code) {
        if(!verifyTotp(userId,code,false))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid verification code");
        jdbc.sql("UPDATE user_mfa_totp SET confirmed_at=now(),updated_at=now() WHERE user_id=:user").param("user",userId).update();
        jdbc.sql("UPDATE users SET mfa_enabled=true,updated_at=now() WHERE id=:user").param("user",userId).update();
        jdbc.sql("DELETE FROM user_mfa_recovery_codes WHERE user_id=:user").param("user",userId).update();
        List<String> codes=new ArrayList<>();
        for(int index=0;index<8;index++){
            byte[] bytes=new byte[10];random.nextBytes(bytes);String value=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).toUpperCase();codes.add(value);
            jdbc.sql("INSERT INTO user_mfa_recovery_codes(user_id,code_hash) VALUES (:user,:hash)").param("user",userId).param("hash",hash(value)).update();
        }
        audit(userId,"mfa.enabled");
        return new RecoveryCodes(List.copyOf(codes));
    }

    @Transactional
    public void disable(UUID userId,String code) {
        if(!verifyLoginCode(userId,code))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid MFA code");
        jdbc.sql("UPDATE users SET mfa_enabled=false,updated_at=now() WHERE id=:user").param("user",userId).update();
        jdbc.sql("DELETE FROM user_mfa_totp WHERE user_id=:user").param("user",userId).update();
        jdbc.sql("DELETE FROM user_mfa_recovery_codes WHERE user_id=:user").param("user",userId).update();
        jdbc.sql("UPDATE auth_sessions SET revoked_at=COALESCE(revoked_at,now()) WHERE user_id=:user").param("user",userId).update();
        audit(userId,"mfa.disabled");
    }

    public Status status(UUID userId) {
        boolean enabled=jdbc.sql("SELECT mfa_enabled FROM users WHERE id=:user").param("user",userId).query(Boolean.class).optional().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
        long remaining=enabled?jdbc.sql("SELECT COUNT(*) FROM user_mfa_recovery_codes WHERE user_id=:user AND used_at IS NULL").param("user",userId).query(Long.class).single():0;
        return new Status(enabled,remaining);
    }

    @Transactional
    public boolean verifyLoginCode(UUID userId,String code) {
        if(code==null||code.isBlank())return false;
        if(code.matches("\\d{6}"))return verifyTotp(userId,code,true);
        int used=jdbc.sql("UPDATE user_mfa_recovery_codes SET used_at=now() WHERE user_id=:user AND code_hash=:hash AND used_at IS NULL")
            .param("user",userId).param("hash",hash(code.trim().toUpperCase())).update();
        if(used==1)audit(userId,"mfa.recovery_code.used");
        return used==1;
    }

    private boolean verifyTotp(UUID userId,String code,boolean requireConfirmed) {
        requireKey();
        String sql="SELECT encrypted_secret,encrypted_data_key,key_version FROM user_mfa_totp WHERE user_id=:user"+(requireConfirmed?" AND confirmed_at IS NOT NULL":"");
        var encrypted=jdbc.sql(sql).param("user",userId).query((rs,row)->new EnvelopeCrypto.Encrypted(rs.getBytes(1),rs.getBytes(2),rs.getString(3))).optional();
        if(encrypted.isEmpty())return false;
        String secret=new String(crypto().decrypt(encrypted.get(),context(userId)),StandardCharsets.UTF_8);
        return totp.verify(secret,code,Instant.now());
    }

    private EnvelopeCrypto crypto(){return new EnvelopeCrypto(masterKey,"env-v1");}
    private void requireKey(){if(masterKey.isBlank())throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"MFA is awaiting KMS/master-key configuration");}
    private String context(UUID userId){return "mfa:"+userId;}
    private String encode(String value){return URLEncoder.encode(value,StandardCharsets.UTF_8).replace("+","%20");}
    private byte[] hash(String value){try{return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));}catch(Exception failure){throw new IllegalStateException(failure);}}
    private void audit(UUID userId,String action){jdbc.sql("INSERT INTO audit_events(organization_id,actor_id,action,target_type,target_id) SELECT om.organization_id,:user,:action,'user',CAST(:user AS text) FROM organization_members om WHERE om.user_id=:user ORDER BY om.organization_id LIMIT 1").param("user",userId).param("action",action).update();}

    public record Setup(String secret,String otpauthUri){}
    public record RecoveryCodes(List<String> recoveryCodes){}
    public record Status(boolean enabled,long recoveryCodesRemaining){}
}
