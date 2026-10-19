package com.flowmind.api.workflow;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class WorkflowGraphValidatorTest {
    private final WorkflowGraphValidator validator = new WorkflowGraphValidator(new NodeRegistry());

    @Test
    void returnsDependencyOrderForDag() {
        var graph = new WorkflowGraph(
            List.of(node("trigger", "manual"), node("classify", "ai.classify"), node("notify", "approval.human")),
            List.of(edge("e1", "trigger", "classify"), edge("e2", "classify", "notify")));
        var result = validator.validate(graph);
        assertThat(result.valid()).isTrue();
        assertThat(result.topologicalOrder()).containsExactly("trigger", "classify", "notify");
    }

    @Test
    void rejectsUnboundedCycle() {
        var graph = new WorkflowGraph(List.of(node("a", "manual"), node("b", "ai.generate")), List.of(edge("e1", "a", "b"), edge("e2", "b", "a")));
        var result = validator.validate(graph);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).contains("Unbounded graph cycle detected; use a bounded Loop node instead");
    }

    @Test
    void validatesLoopBounds() {
        var loop = new WorkflowGraph.Node("loop", "loop", 1, Map.of("maxIterations", 0), false);
        assertThat(validator.validate(new WorkflowGraph(List.of(loop), List.of())).valid()).isFalse();
    }

    @Test
    void rejectsDeclaredNodesWithoutRuntimeAdapters() {
        var result = validator.validate(new WorkflowGraph(List.of(node("notify", "slack")), List.of()));
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).contains("Node type slack does not have a runtime adapter yet");
    }

    @Test
    void rejectsAiCapabilitiesThatAreNotChatGeneration() {
        var result = validator.validate(new WorkflowGraph(List.of(node("embed", "ai.embeddings")), List.of()));
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).contains("Node type ai.embeddings does not have a runtime adapter yet");
    }

    private WorkflowGraph.Node node(String id, String type) { return new WorkflowGraph.Node(id, type, 1, Map.of(), false); }
    private WorkflowGraph.Edge edge(String id, String source, String target) { return new WorkflowGraph.Edge(id, source, target, null); }
}

