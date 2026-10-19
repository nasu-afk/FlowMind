package com.flowmind.api.identity;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

@Component
@ConditionalOnWebApplication(type=ConditionalOnWebApplication.Type.SERVLET)
public class AuthRateLimitFilter extends OncePerRequestFilter {
    private static final Set<String> LIMITED_PATHS=Set.of(
        "/api/auth/register", "/api/auth/login", "/api/auth/refresh", "/api/auth/oidc/exchange"
    );
    private static final DefaultRedisScript<Long> SCRIPT=new DefaultRedisScript<>("""
        local current = redis.call('INCR', KEYS[1])
        if current == 1 then redis.call('EXPIRE', KEYS[1], ARGV[2]) end
        if current > tonumber(ARGV[1]) then return -1 end
        return tonumber(ARGV[1]) - current
        """,Long.class);

    private final StringRedisTemplate redis;
    private final long limit;
    private final long windowSeconds;

    public AuthRateLimitFilter(StringRedisTemplate redis,
                               @Value("${flowmind.security.auth-rate-limit.requests:20}") long limit,
                               @Value("${flowmind.security.auth-rate-limit.window-seconds:60}") long windowSeconds){
        if(limit<1||windowSeconds<1)throw new IllegalArgumentException("Authentication rate limits must be positive");
        this.redis=redis;this.limit=limit;this.windowSeconds=windowSeconds;
    }

    @Override protected boolean shouldNotFilter(HttpServletRequest request){
        return !"POST".equals(request.getMethod())||!LIMITED_PATHS.contains(request.getRequestURI());
    }

    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        String key="flowmind:auth-limit:"+sha256(request.getRemoteAddr()+":"+request.getRequestURI());
        Long remaining;
        try{
            remaining=redis.execute(SCRIPT,List.of(key),Long.toString(limit),Long.toString(windowSeconds));
            if(remaining==null)throw new RedisConnectionFailureException("Rate limiter returned no result");
        }catch(RuntimeException ex){
            response.setStatus(503);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"error\":\"rate_limiter_unavailable\",\"message\":\"Authentication is temporarily unavailable\"}");
            return;
        }
        response.setHeader("X-RateLimit-Limit",Long.toString(limit));
        response.setHeader("X-RateLimit-Remaining",Long.toString(Math.max(remaining,0)));
        if(remaining<0){
            response.setStatus(429);
            response.setHeader("Retry-After",Long.toString(windowSeconds));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"error\":\"rate_limit_exceeded\",\"message\":\"Too many authentication attempts\"}");
            return;
        }
        chain.doFilter(request,response);
    }

    private static String sha256(String value){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
        catch(Exception ex){throw new IllegalStateException("SHA-256 unavailable",ex);}
    }
}
