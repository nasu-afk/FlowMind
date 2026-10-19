package com.flowmind.api.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService service;
    private final MfaService mfa;
    public AuthController(AuthService service,MfaService mfa){this.service=service;this.mfa=mfa;}
    @PostMapping("/register") @ResponseStatus(HttpStatus.CREATED)
    public AuthService.Tokens register(@Valid @RequestBody RegisterRequest request,HttpServletRequest http){return service.register(new AuthService.Register(request.email(),request.displayName(),request.password(),request.organizationName()),http.getHeader("User-Agent"));}
    @PostMapping("/login") public AuthService.Tokens login(@Valid @RequestBody LoginRequest request,HttpServletRequest http){return service.login(new AuthService.Login(request.email(),request.password(),request.mfaCode()),http.getHeader("User-Agent"));}
    @PostMapping("/refresh") public AuthService.Tokens refresh(@Valid @RequestBody RefreshRequest request,HttpServletRequest http){return service.refresh(request.refreshToken(),http.getHeader("User-Agent"));}
    @PostMapping("/logout") @ResponseStatus(HttpStatus.NO_CONTENT) public void logout(@Valid @RequestBody RefreshRequest request){service.logout(request.refreshToken());}
    public record RegisterRequest(@Email @NotBlank String email,@NotBlank @Size(max=120) String displayName,@NotBlank @Size(min=12,max=128) String password,@NotBlank @Size(max=120) String organizationName) {}
    @PostMapping("/mfa/totp/setup") public MfaService.Setup setupMfa(@AuthenticationPrincipal Jwt jwt){return mfa.setup(java.util.UUID.fromString(jwt.getSubject()));}
    @PostMapping("/mfa/totp/confirm") public MfaService.RecoveryCodes confirmMfa(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody MfaCodeRequest request){return mfa.confirm(java.util.UUID.fromString(jwt.getSubject()),request.code());}
    @PostMapping("/mfa/totp/disable") @ResponseStatus(HttpStatus.NO_CONTENT) public void disableMfa(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody MfaCodeRequest request){mfa.disable(java.util.UUID.fromString(jwt.getSubject()),request.code());}
    @GetMapping("/mfa/status") public MfaService.Status mfaStatus(@AuthenticationPrincipal Jwt jwt){return mfa.status(java.util.UUID.fromString(jwt.getSubject()));}
    public record LoginRequest(@Email @NotBlank String email,@NotBlank String password,String mfaCode) {}
    public record RefreshRequest(@NotBlank String refreshToken) {}
    public record MfaCodeRequest(@NotBlank @Size(max=32) String code) {}
}
