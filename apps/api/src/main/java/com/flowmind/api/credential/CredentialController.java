package com.flowmind.api.credential;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/credentials")
public class CredentialController {
    private final CredentialService service; public CredentialController(CredentialService service){this.service=service;}
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public CredentialService.CredentialView create(@PathVariable UUID workspaceId,@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody Request request){return service.create(workspaceId,UUID.fromString(jwt.getSubject()),new CredentialService.CreateCredential(request.provider(),request.name(),request.secret()));}
    @GetMapping public List<CredentialService.CredentialView> list(@PathVariable UUID workspaceId,@AuthenticationPrincipal Jwt jwt){return service.list(workspaceId,UUID.fromString(jwt.getSubject()));}
    @DeleteMapping("/{credentialId}") @ResponseStatus(HttpStatus.NO_CONTENT) public void revoke(@PathVariable UUID workspaceId,@PathVariable UUID credentialId,@AuthenticationPrincipal Jwt jwt){service.revoke(workspaceId,credentialId,UUID.fromString(jwt.getSubject()));}
    public record Request(@NotBlank @Size(max=120) String provider,@NotBlank @Size(max=120) String name,@NotEmpty Map<String,String> secret){}
}

