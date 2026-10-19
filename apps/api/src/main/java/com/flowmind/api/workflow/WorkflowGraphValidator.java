package com.flowmind.api.workflow;

import org.springframework.stereotype.Component;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class WorkflowGraphValidator {
    private final NodeRegistry registry;

    public WorkflowGraphValidator(NodeRegistry registry) {
        this.registry = registry;
    }

    public ValidationResult validate(WorkflowGraph graph) {
        var errors = new ArrayList<String>();
        var ids = new HashSet<String>();
        for (var node : graph.nodes()) {
            if (!ids.add(node.id())) errors.add("Duplicate node id: " + node.id());
            var definition = registry.find(node.type());
            if (definition.isEmpty()) errors.add("Unknown node type: " + node.type());
            else if (!definition.get().runtimeAvailable()) errors.add("Node type " + node.type() + " does not have a runtime adapter yet");
            if ("loop".equals(node.type()) && !validLoopBound(node)) {
                errors.add("Loop node " + node.id() + " requires maxIterations between 1 and 1000");
            }
        }
        var edgeIds = new HashSet<String>();
        for (var edge : graph.edges()) {
            if (!edgeIds.add(edge.id())) errors.add("Duplicate edge id: " + edge.id());
            if (!ids.contains(edge.source())) errors.add("Unknown edge source: " + edge.source());
            if (!ids.contains(edge.target())) errors.add("Unknown edge target: " + edge.target());
            if (edge.source().equals(edge.target())) errors.add("Self edge is not allowed: " + edge.id());
        }
        var order = errors.isEmpty() ? topologicalOrder(graph, errors) : List.<String>of();
        return new ValidationResult(errors.isEmpty(), List.copyOf(errors), order);
    }

    private boolean validLoopBound(WorkflowGraph.Node node) {
        Object raw = node.configuration().get("maxIterations");
        return raw instanceof Number n && n.intValue() >= 1 && n.intValue() <= 1000;
    }

    private List<String> topologicalOrder(WorkflowGraph graph, List<String> errors) {
        Map<String, Integer> inDegree = new HashMap<>();
        Map<String, List<String>> outgoing = new HashMap<>();
        graph.nodes().forEach(n -> { inDegree.put(n.id(), 0); outgoing.put(n.id(), new ArrayList<>()); });
        graph.edges().forEach(e -> {
            outgoing.get(e.source()).add(e.target());
            inDegree.compute(e.target(), (ignored, value) -> value + 1);
        });
        var ready = new ArrayDeque<String>();
        inDegree.forEach((id, degree) -> { if (degree == 0) ready.add(id); });
        var result = new ArrayList<String>();
        while (!ready.isEmpty()) {
            var current = ready.removeFirst();
            result.add(current);
            for (var next : outgoing.get(current)) {
                int remaining = inDegree.compute(next, (ignored, value) -> value - 1);
                if (remaining == 0) ready.addLast(next);
            }
        }
        if (result.size() != graph.nodes().size()) {
            errors.add("Unbounded graph cycle detected; use a bounded Loop node instead");
            return List.of();
        }
        return List.copyOf(result);
    }

    public record ValidationResult(boolean valid, List<String> errors, List<String> topologicalOrder) {}
}

