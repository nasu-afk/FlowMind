package com.flowmind.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpMethod;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import com.flowmind.api.identity.ApiKeyAuthenticationFilter;
import com.flowmind.api.identity.ApiKeyScopeAuthorizationFilter;
import com.flowmind.api.identity.OidcAuthenticationSuccessHandler;
import com.flowmind.api.identity.AuthRateLimitFilter;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Configuration
public class SecurityConfig {
    @Bean
    @ConditionalOnWebApplication(type=ConditionalOnWebApplication.Type.SERVLET)
    SecurityFilterChain securityFilterChain(HttpSecurity http,ApiKeyAuthenticationFilter apiKeyFilter,
                                            ApiKeyScopeAuthorizationFilter apiKeyScopeFilter,
                                            AuthRateLimitFilter authRateLimitFilter,
                                            ObjectProvider<OidcAuthenticationSuccessHandler> oidcSuccess,
                                            @Value("${flowmind.oidc.enabled:false}") boolean oidcEnabled,
                                            @Value("${flowmind.web-base-url:http://localhost:3002}") String webBaseUrl) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(oidcEnabled?SessionCreationPolicy.IF_REQUIRED:SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/error", "/actuator/health/**").permitAll()
                .requestMatchers(HttpMethod.GET,"/api/auth/oidc/status").permitAll()
                .requestMatchers(HttpMethod.POST,"/api/auth/register","/api/auth/login","/api/auth/refresh","/api/auth/logout","/api/auth/oidc/exchange").permitAll()
                .requestMatchers("/oauth2/authorization/**","/login/oauth2/code/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/webhooks/**").permitAll()
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()))
            .addFilterBefore(authRateLimitFilter, BearerTokenAuthenticationFilter.class)
            .addFilterBefore(apiKeyFilter, BearerTokenAuthenticationFilter.class)
            .addFilterAfter(apiKeyScopeFilter, ApiKeyAuthenticationFilter.class);
        if(oidcEnabled)http.oauth2Login(oauth->oauth.successHandler(oidcSuccess.getObject()).failureHandler((request,response,failure)->response.sendRedirect(webBaseUrl.replaceAll("/+$","")+"/sign-in?error=oidc_failed")));
        return http.build();
    }

    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }

    @Bean SecretKey jwtSecretKey(@Value("${flowmind.security.jwt-secret}") String value) throws Exception {
        byte[] key=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(key,"HmacSHA256");
    }
    @Bean JwtDecoder jwtDecoder(SecretKey key,@Value("${flowmind.security.issuer}") String issuer,@Value("${flowmind.security.audience}") String audience) {
        var decoder=NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        OAuth2TokenValidator<Jwt> audienceValidator=token -> token.getAudience().contains(audience)
            ? OAuth2TokenValidatorResult.success()
            : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token","Required audience is missing",null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer),audienceValidator));
        return decoder;
    }
    @Bean JwtEncoder jwtEncoder(SecretKey key) { return new NimbusJwtEncoder(new ImmutableSecret<>(key)); }
}

