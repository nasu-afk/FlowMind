package com.flowmind.api.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Component
public class AiOutputNormalizer {
    private final ObjectMapper json;

    public AiOutputNormalizer(ObjectMapper json) {
        this.json = json;
    }

    public Object normalize(JsonNode providerResponse, Map<String, Object> configuration) {
        JsonNode contentNode = providerResponse.at("/choices/0/message/content");
        if (!contentNode.isTextual()) {
            throw new IllegalStateException("AI service response did not contain choices[0].message.content");
        }

        String content = stripCodeFence(contentNode.asText());
        JsonNode parsed = parseJson(content);
        Object schemaValue = configuration.get("responseSchema");
        if (schemaValue != null) {
            if (parsed == null) throw new IllegalStateException("AI response must be valid JSON because a response schema is configured");
            JsonNode schema = json.valueToTree(schemaValue);
            var errors = new ArrayList<String>();
            validate(parsed, schema, "$", errors);
            if (!errors.isEmpty()) throw new IllegalStateException("AI response schema validation failed: " + String.join("; ", errors));
        }

        if (parsed != null) return json.convertValue(parsed, Object.class);
        return Map.of("content", content);
    }

    private JsonNode parseJson(String content) {
        try { return json.readTree(content); }
        catch (Exception ignored) { return null; }
    }

    private String stripCodeFence(String value) {
        String trimmed = value.trim();
        if (!trimmed.startsWith("```")) return trimmed;
        int firstLineEnd = trimmed.indexOf('\n');
        int closingFence = trimmed.lastIndexOf("```");
        if (firstLineEnd < 0 || closingFence <= firstLineEnd) return trimmed;
        return trimmed.substring(firstLineEnd + 1, closingFence).trim();
    }

    private void validate(JsonNode value, JsonNode schema, String path, List<String> errors) {
        String type = schema.path("type").asText("");
        if (!type.isBlank() && !matchesType(value, type)) {
            errors.add(path + " must be " + type);
            return;
        }

        JsonNode enumValues = schema.get("enum");
        if (enumValues != null && enumValues.isArray()) {
            boolean matched = false;
            for (JsonNode allowed : enumValues) if (allowed.equals(value)) { matched = true; break; }
            if (!matched) errors.add(path + " must be one of " + enumValues);
        }

        if (value.isObject()) {
            JsonNode required = schema.get("required");
            if (required != null && required.isArray()) {
                for (JsonNode field : required) if (!value.has(field.asText())) errors.add(path + "." + field.asText() + " is required");
            }
            JsonNode properties = schema.get("properties");
            if (properties != null && properties.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> fields = properties.properties().iterator();
                while (fields.hasNext()) {
                    var field = fields.next();
                    if (value.has(field.getKey())) validate(value.get(field.getKey()), field.getValue(), path + "." + field.getKey(), errors);
                }
                if (schema.path("additionalProperties").isBoolean() && !schema.path("additionalProperties").asBoolean()) {
                    value.fieldNames().forEachRemaining(name -> { if (!properties.has(name)) errors.add(path + "." + name + " is not allowed"); });
                }
            }
        }

        if (value.isArray() && schema.has("items")) {
            for (int index = 0; index < value.size(); index++) validate(value.get(index), schema.get("items"), path + "[" + index + "]", errors);
        }
    }

    private boolean matchesType(JsonNode value, String type) {
        return switch (type) {
            case "object" -> value.isObject();
            case "array" -> value.isArray();
            case "string" -> value.isTextual();
            case "number" -> value.isNumber();
            case "integer" -> value.isIntegralNumber();
            case "boolean" -> value.isBoolean();
            case "null" -> value.isNull();
            default -> false;
        };
    }
}
