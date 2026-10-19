package com.flowmind.api.workflow;

import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class NodeRegistry {
    public enum Category { TRIGGER, LOGIC, DATA, AI, INTEGRATION }

    public record NodeDefinition(String type, int version, Category category, boolean sideEffecting, String credentialType, boolean runtimeAvailable) {}

    private final Map<String, NodeDefinition> definitions;

    public NodeRegistry() {
        this.definitions = allDefinitions().stream().collect(Collectors.toUnmodifiableMap(NodeDefinition::type, Function.identity()));
    }

    public Optional<NodeDefinition> find(String type) { return Optional.ofNullable(definitions.get(type)); }
    public List<NodeDefinition> list() { return definitions.values().stream().sorted(java.util.Comparator.comparing(NodeDefinition::type)).toList(); }

    private static List<NodeDefinition> allDefinitions() {
        return List.of(
            runtime("manual", Category.TRIGGER), runtime("webhook", Category.TRIGGER), runtime("trigger.webhook", Category.TRIGGER), runtime("schedule", Category.TRIGGER), runtime("form", Category.TRIGGER),
            safe("email.received", Category.TRIGGER), safe("github.event", Category.TRIGGER), safe("database.poll", Category.TRIGGER), safe("application.event", Category.TRIGGER),
            runtime("if", Category.LOGIC), runtime("switch", Category.LOGIC), runtime("logic.condition", Category.LOGIC), safe("filter", Category.LOGIC), safe("merge", Category.LOGIC), safe("split", Category.LOGIC),
            safe("delay", Category.LOGIC), safe("loop", Category.LOGIC), safe("batch", Category.LOGIC), safe("aggregate", Category.LOGIC), safe("transform", Category.LOGIC),
            safe("sort", Category.LOGIC), safe("deduplicate", Category.LOGIC), safe("error.handler", Category.LOGIC), safe("subworkflow", Category.LOGIC), runtimeAction("human.approval", Category.LOGIC, null), runtimeAction("approval.human", Category.LOGIC, null),
            safe("json.parse", Category.DATA), safe("csv.parse", Category.DATA), safe("xml.parse", Category.DATA), safe("text.transform", Category.DATA),
            safe("datetime", Category.DATA), safe("math", Category.DATA), safe("data.validate", Category.DATA), safe("schema.convert", Category.DATA), safe("file.metadata", Category.DATA), safe("data.aggregate", Category.DATA),
            ai("ai.generate"), ai("ai.classify"), ai("ai.summarize"), ai("ai.extract"), ai("ai.translate"), unavailableAi("ai.embeddings"), unavailableAi("ai.rag"), unavailableAi("ai.agent"), unavailableAi("ai.tool"), ai("ai.structured"), unavailableAi("ai.document"), ai("ai.sentiment"),
            action("http.request", Category.INTEGRATION, null), action("webhook.response", Category.INTEGRATION, null), action("email.send", Category.INTEGRATION, "email"),
            action("slack", Category.INTEGRATION, "slack"), action("discord", Category.INTEGRATION, "discord"), action("telegram", Category.INTEGRATION, "telegram"),
            action("github", Category.INTEGRATION, "github"), action("gitlab", Category.INTEGRATION, "gitlab"), action("google.sheets", Category.INTEGRATION, "google"),
            action("google.drive", Category.INTEGRATION, "google"), action("google.calendar", Category.INTEGRATION, "google"), action("gmail", Category.INTEGRATION, "google"),
            action("notion", Category.INTEGRATION, "notion"), action("postgresql", Category.INTEGRATION, "postgresql"), action("mysql", Category.INTEGRATION, "mysql"),
            action("mongodb", Category.INTEGRATION, "mongodb"), action("s3", Category.INTEGRATION, "s3"), action("rest", Category.INTEGRATION, null)
        );
    }

    private static NodeDefinition runtime(String type, Category category) { return new NodeDefinition(type, 1, category, false, null, true); }
    private static NodeDefinition safe(String type, Category category) { return new NodeDefinition(type, 1, category, false, null, false); }
    private static NodeDefinition ai(String type) { return new NodeDefinition(type, 1, Category.AI, false, "ai-provider", true); }
    private static NodeDefinition unavailableAi(String type) { return new NodeDefinition(type, 1, Category.AI, false, "ai-provider", false); }
    private static NodeDefinition action(String type, Category category, String credential) { return new NodeDefinition(type, 1, category, true, credential, false); }
    private static NodeDefinition runtimeAction(String type, Category category, String credential) { return new NodeDefinition(type, 1, category, true, credential, true); }
}

