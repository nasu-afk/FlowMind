package com.flowmind.api.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth/oidc")
public class OidcController {
    private final OidcIdentityService identities; private final boolean enabled;
    public OidcController(OidcIdentityService identities,@Value("${flowmind.oidc.enabled:false}") boolean enabled){this.identities=identities;this.enabled=enabled;}
    @GetMapping("/status") public Status status(){return new Status(enabled);}
    @PostMapping("/exchange") public AuthService.Tokens exchange(@Valid @RequestBody Exchange request,HttpServletRequest http){return identities.exchange(request.code(),http.getHeader("User-Agent"));}
    public record Status(boolean enabled){}
    public record Exchange(@NotBlank String code){}
}
