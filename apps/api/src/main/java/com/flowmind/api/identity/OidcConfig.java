package com.flowmind.api.identity;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.ClientRegistrations;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;

@Configuration
@ConditionalOnProperty(name="flowmind.oidc.enabled",havingValue="true")
public class OidcConfig {
    @Bean
    ClientRegistrationRepository clientRegistrationRepository(
        @Value("${flowmind.oidc.issuer-uri}") String issuer,
        @Value("${flowmind.oidc.client-id}") String clientId,
        @Value("${flowmind.oidc.client-secret}") String clientSecret
    ) {
        if(issuer.isBlank()||clientId.isBlank()||clientSecret.isBlank())throw new IllegalStateException("OIDC is enabled but issuer/client credentials are incomplete");
        ClientRegistration registration=ClientRegistrations.fromIssuerLocation(issuer)
            .registrationId("enterprise").clientId(clientId).clientSecret(clientSecret).scope("openid","profile","email").build();
        return new InMemoryClientRegistrationRepository(registration);
    }
}
