package com.flowmind.api.identity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApiKeyScopeAuthorizationFilterTest {
    @Test void mapsSafeRequestsToReadScope() {
        assertEquals("read", ApiKeyScopeAuthorizationFilter.requiredScope("GET", "/api/workspaces/1/workflows"));
        assertEquals("read", ApiKeyScopeAuthorizationFilter.requiredScope("HEAD", "/api/workspaces/1/workflows"));
    }

    @Test void mapsMutationsToWriteScope() {
        assertEquals("write", ApiKeyScopeAuthorizationFilter.requiredScope("POST", "/api/workspaces/1/workflows"));
        assertEquals("write", ApiKeyScopeAuthorizationFilter.requiredScope("DELETE", "/api/workspaces/1/credentials/2"));
    }

    @Test void protectsApiKeyAdministration() {
        assertEquals("admin", ApiKeyScopeAuthorizationFilter.requiredScope("GET", "/api/organizations/1/api-keys"));
        assertEquals("admin", ApiKeyScopeAuthorizationFilter.requiredScope("DELETE", "/api/organizations/1/api-keys/2"));
    }
}
