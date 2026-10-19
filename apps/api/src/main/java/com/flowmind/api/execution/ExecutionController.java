package com.flowmind.api.execution;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}")
public class ExecutionController {
    private final ExecutionService service;
    public ExecutionController(ExecutionService service) { this.service=service; }
    @PostMapping("/workflows/{workflowId}/executions")
    public ExecutionService.ExecutionView start(@PathVariable UUID workspaceId,@PathVariable UUID workflowId,@AuthenticationPrincipal Jwt jwt,@RequestBody ExecutionService.StartExecution request) {
        return service.start(workspaceId,workflowId,UUID.fromString(jwt.getSubject()),request);
    }
    @GetMapping("/executions")
    public List<ExecutionService.ExecutionView> list(@PathVariable UUID workspaceId,@AuthenticationPrincipal Jwt jwt) {
        return service.list(workspaceId,UUID.fromString(jwt.getSubject()));
    }
    @GetMapping("/executions/{executionId}")
    public ExecutionService.ExecutionDetails get(@PathVariable UUID workspaceId,@PathVariable UUID executionId,@AuthenticationPrincipal Jwt jwt) {
        return service.get(workspaceId,executionId,UUID.fromString(jwt.getSubject()));
    }
    @PostMapping("/executions/{executionId}/cancel")
    public ExecutionService.ExecutionView cancel(@PathVariable UUID workspaceId,@PathVariable UUID executionId,@AuthenticationPrincipal Jwt jwt,@RequestBody(required=false) Map<String,Object> details) {
        return service.cancel(workspaceId,executionId,UUID.fromString(jwt.getSubject()),details);
    }
}

