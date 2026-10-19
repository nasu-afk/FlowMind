package com.flowmind.api.identity;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class ApiKeyScopeAuthorizationFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        Authentication authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt && jwt.hasClaim("api_key_id")) {
            String required = requiredScope(request.getMethod(), request.getRequestURI());
            boolean allowed = authentication.getAuthorities().stream().anyMatch(authority ->
                authority.getAuthority().equals("SCOPE_admin") || authority.getAuthority().equals("SCOPE_" + required));
            if (!allowed) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write("{\"error\":\"insufficient_scope\",\"message\":\"API key lacks the required scope\"}");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    static String requiredScope(String method, String path) {
        if (path.matches("/api/organizations/[^/]+/api-keys(?:/.*)?")) return "admin";
        return "GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method) ? "read" : "write";
    }
}
