package com.flowmind.api.identity;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {
    private final JdbcClient jdbc; public ApiKeyAuthenticationFilter(JdbcClient jdbc){this.jdbc=jdbc;}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
        String supplied=request.getHeader("X-FlowMind-Key");
        if(supplied!=null&&SecurityContextHolder.getContext().getAuthentication()==null){var parts=supplied.split("\\.",2);if(parts.length==2){var stored=jdbc.sql("SELECT id,organization_id,created_by,secret_hash,scopes,expires_at FROM api_keys WHERE key_prefix=:prefix AND revoked_at IS NULL AND (expires_at IS NULL OR expires_at>now())").param("prefix",parts[0]).query((rs,row)->new Stored(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getObject(3,UUID.class),rs.getBytes(4),(String[])rs.getArray(5).getArray(),rs.getTimestamp(6)==null?null:rs.getTimestamp(6).toInstant())).optional();if(stored.isPresent()&&MessageDigest.isEqual(stored.get().hash(),sha256(parts[1]))){Instant now=Instant.now();var claims=Map.<String,Object>of("sub",stored.get().actor().toString(),"organization_id",stored.get().organization().toString(),"api_key_id",stored.get().id().toString(),"scope",List.of(stored.get().scopes()));var token=new Jwt(supplied,now,stored.get().expires()==null?now.plusSeconds(300):stored.get().expires(),Map.of("alg","API_KEY"),claims);var authorities=java.util.Arrays.stream(stored.get().scopes()).map(scope->new SimpleGrantedAuthority("SCOPE_"+scope)).toList();SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(token,supplied,authorities));jdbc.sql("UPDATE api_keys SET last_used_at=now() WHERE id=:id").param("id",stored.get().id()).update();}}}
        chain.doFilter(request,response);
    }
    private byte[] sha256(String value){try{return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));}catch(Exception impossible){throw new IllegalStateException(impossible);}}
    record Stored(UUID id,UUID organization,UUID actor,byte[] hash,String[] scopes,Instant expires){}
}

