package com.flowmind.api.workflow;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/node-types")
public class NodeRegistryController {
    private final NodeRegistry registry;
    public NodeRegistryController(NodeRegistry registry) { this.registry = registry; }
    @GetMapping public List<NodeRegistry.NodeDefinition> list() { return registry.list(); }
}

