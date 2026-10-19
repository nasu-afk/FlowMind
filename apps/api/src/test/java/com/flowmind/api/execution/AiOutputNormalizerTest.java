package com.flowmind.api.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiOutputNormalizerTest {
    private final ObjectMapper json = new ObjectMapper();
    private final AiOutputNormalizer normalizer = new AiOutputNormalizer(json);

    @Test
    void extractsFencedStructuredContentFromGroqEnvelope() throws Exception {
        var response = json.readTree("""
            {"choices":[{"message":{"content":"```json\\n{\\\"intent\\\":\\\"account access\\\",\\\"urgency\\\":\\\"critical\\\"}\\n```"}}]}
            """);
        var schema = Map.of(
            "type", "object",
            "required", List.of("intent", "urgency"),
            "additionalProperties", false,
            "properties", Map.of(
                "intent", Map.of("type", "string"),
                "urgency", Map.of("type", "string", "enum", List.of("low", "medium", "high", "critical"))));

        assertThat(normalizer.normalize(response, Map.of("responseSchema", schema)))
            .isEqualTo(Map.of("intent", "account access", "urgency", "critical"));
    }

    @Test
    void rejectsInvalidStructuredContent() throws Exception {
        var response = json.readTree("{\"choices\":[{\"message\":{\"content\":\"{\\\"urgency\\\":\\\"urgent\\\"}\"}}]}");
        var schema = Map.of(
            "type", "object",
            "required", List.of("intent", "urgency"),
            "properties", Map.of(
                "intent", Map.of("type", "string"),
                "urgency", Map.of("type", "string", "enum", List.of("low", "high"))));

        assertThatThrownBy(() -> normalizer.normalize(response, Map.of("responseSchema", schema)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("$.intent is required")
            .hasMessageContaining("$.urgency must be one of");
    }

    @Test
    void wrapsPlainTextForDownstreamNodes() throws Exception {
        var response = json.readTree("{\"choices\":[{\"message\":{\"content\":\"A concise reply\"}}]}");
        assertThat(normalizer.normalize(response, Map.of())).isEqualTo(Map.of("content", "A concise reply"));
    }
}
