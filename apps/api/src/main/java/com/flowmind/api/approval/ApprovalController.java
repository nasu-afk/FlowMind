package com.flowmind.api.approval;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
import java.util.List;

@RestController
@RequestMapping("/api")
public class ApprovalController {
    private final ApprovalService service;
    public ApprovalController(ApprovalService service){this.service=service;}
    @PostMapping("/approvals/{id}/decision") public ApprovalService.DecisionResult decide(@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody DecisionRequest request){return service.decide(id,UUID.fromString(jwt.getSubject()),request.decision(),request.comment());}
    @GetMapping("/workspaces/{workspaceId}/approvals") public List<ApprovalService.ApprovalView> list(@PathVariable UUID workspaceId,@AuthenticationPrincipal Jwt jwt,@RequestParam(required=false) String status){return service.list(workspaceId,UUID.fromString(jwt.getSubject()),status);}
    public record DecisionRequest(@NotNull ApprovalService.Decision decision,@Size(max=2000) String comment){}
}
