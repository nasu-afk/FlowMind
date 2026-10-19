package com.flowmind.api.workflow;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.Map;

public record WorkflowGraph(@NotEmpty List<@Valid Node> nodes, List<@Valid Edge> edges) {
    public WorkflowGraph {
        edges = edges == null ? List.of() : List.copyOf(edges);
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
    }

    public record Node(
        @NotBlank String id,
        @NotBlank String type,
        int version,
        Map<String, Object> configuration,
        boolean sideEffecting
    ) {
        public Node {
            configuration = configuration == null ? Map.of() : Map.copyOf(configuration);
            if (version < 1) throw new IllegalArgumentException("Node version must be positive");
        }
    }

    public record Edge(@NotBlank String id, @NotBlank String source, @NotBlank String target, String branch) {}
}

