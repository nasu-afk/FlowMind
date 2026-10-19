package com.flowmind.api.workflow;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
import java.util.List;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/workflows")
public class WorkflowController {
    private final WorkflowService service;
    private final WorkflowGraphValidator validator;

    public WorkflowController(WorkflowService service, WorkflowGraphValidator validator) {
        this.service = service;
        this.validator = validator;
    }

    @PostMapping("/validate")
    public WorkflowGraphValidator.ValidationResult validate(@Valid @RequestBody WorkflowGraph graph) {
        return validator.validate(graph);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WorkflowService.WorkflowView create(
        @PathVariable UUID workspaceId,
        @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody WorkflowService.CreateWorkflow request
    ) {
        return service.create(workspaceId, UUID.fromString(jwt.getSubject()), request);
    }

    @GetMapping
    public List<WorkflowService.WorkflowSummary> list(@PathVariable UUID workspaceId, @AuthenticationPrincipal Jwt jwt) {
        return service.list(workspaceId, UUID.fromString(jwt.getSubject()));
    }

    @GetMapping("/{workflowId}")
    public WorkflowService.WorkflowView get(@PathVariable UUID workspaceId, @PathVariable UUID workflowId, @AuthenticationPrincipal Jwt jwt) {
        return service.get(workspaceId, workflowId, UUID.fromString(jwt.getSubject()));
    }

    @PutMapping("/{workflowId}")
    public WorkflowService.WorkflowView update(@PathVariable UUID workspaceId, @PathVariable UUID workflowId, @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody WorkflowService.UpdateWorkflow request) {
        return service.update(workspaceId, workflowId, UUID.fromString(jwt.getSubject()), request);
    }

    @PostMapping("/{workflowId}/publish")
    public WorkflowService.PublishedWorkflow publish(
        @PathVariable UUID workspaceId,
        @PathVariable UUID workflowId,
        @AuthenticationPrincipal Jwt jwt
    ) {
        return service.publish(workspaceId, workflowId, UUID.fromString(jwt.getSubject()));
    }
}

