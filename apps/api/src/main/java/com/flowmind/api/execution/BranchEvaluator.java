package com.flowmind.api.execution;

import com.flowmind.api.workflow.WorkflowGraph;
import org.springframework.stereotype.Component;
import java.util.Collection;
import java.util.Map;

@Component
public class BranchEvaluator {
    public BranchDecision evaluate(WorkflowGraph.Node node, Map<String,Object> input, Map<String,Object> outputs) {
        String field = String.valueOf(node.configuration().getOrDefault("field", "priority"));
        Object actual = resolve(field, input, outputs);
        String operator = String.valueOf(node.configuration().getOrDefault("operator", "equals"));
        Object expected = node.configuration().get("value");
        boolean matched = compare(actual, expected, operator);
        String selected = matched
            ? String.valueOf(node.configuration().getOrDefault("trueBranch", "true"))
            : String.valueOf(node.configuration().getOrDefault("falseBranch", "false"));
        return new BranchDecision(selected, matched, actual);
    }

    private Object resolve(String path, Map<String,Object> input, Map<String,Object> outputs) {
        Object current;
        String normalized;
        if (path.startsWith("outputs.")) { current = outputs; normalized = path.substring(8); }
        else if (path.startsWith("input.")) { current = input; normalized = path.substring(6); }
        else { current = input; normalized = path; }
        for (String part : normalized.split("\\.")) {
            if (!(current instanceof Map<?,?> map)) return null;
            current = map.get(part);
        }
        return current;
    }

    private boolean compare(Object actual, Object expected, String operator) {
        return switch (operator.toLowerCase()) {
            case "exists" -> actual != null;
            case "not_exists" -> actual == null;
            case "not_equals", "neq" -> !equal(actual, expected);
            case "contains" -> actual instanceof String text && expected != null && text.contains(String.valueOf(expected))
                || actual instanceof Collection<?> values && values.contains(expected);
            case "gt", "greater_than" -> number(actual) > number(expected);
            case "gte", "greater_than_or_equal" -> number(actual) >= number(expected);
            case "lt", "less_than" -> number(actual) < number(expected);
            case "lte", "less_than_or_equal" -> number(actual) <= number(expected);
            default -> equal(actual, expected);
        };
    }

    private boolean equal(Object left, Object right) {
        if (left instanceof Number || right instanceof Number) return Double.compare(number(left), number(right)) == 0;
        return java.util.Objects.equals(left == null ? null : String.valueOf(left), right == null ? null : String.valueOf(right));
    }

    private double number(Object value) {
        if (value instanceof Number number) return number.doubleValue();
        try { return Double.parseDouble(String.valueOf(value)); }
        catch (RuntimeException invalid) { return Double.NaN; }
    }

    public record BranchDecision(String selectedBranch, boolean matched, Object actualValue) {}
}
