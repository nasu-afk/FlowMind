package com.flowmind.api.identity;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
@ConditionalOnProperty(name="flowmind.oidc.enabled",havingValue="true")
public class OidcAuthenticationSuccessHandler implements AuthenticationSuccessHandler {
    private final OidcIdentityService identities; private final String webBaseUrl;
    public OidcAuthenticationSuccessHandler(OidcIdentityService identities,@Value("${flowmind.web-base-url:http://localhost:3002}") String webBaseUrl){this.identities=identities;this.webBaseUrl=webBaseUrl.replaceAll("/+$","");}

    @Override public void onAuthenticationSuccess(HttpServletRequest request,HttpServletResponse response,Authentication authentication) throws IOException,ServletException {
        if(!(authentication.getPrincipal() instanceof OidcUser user))throw new ServletException("OIDC principal was not returned by provider");
        Object verified=user.getClaim("email_verified");
        boolean emailVerified=Boolean.TRUE.equals(verified)||"true".equalsIgnoreCase(String.valueOf(verified));
        String code=identities.complete(user.getIssuer().toString(),user.getSubject(),user.getEmail(),user.getFullName(),emailVerified);
        if(request.getSession(false)!=null)request.getSession(false).invalidate();
        SecurityContextHolder.clearContext();
        response.sendRedirect(webBaseUrl+"/api/session/oidc/callback?code="+URLEncoder.encode(code,StandardCharsets.UTF_8));
    }
}
