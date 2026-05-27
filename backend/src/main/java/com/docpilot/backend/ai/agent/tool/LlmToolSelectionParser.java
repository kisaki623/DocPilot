package com.docpilot.backend.ai.agent.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class LlmToolSelectionParser {

    private static final Set<String> ALLOWED_DECISIONS = Set.of("status_only", "summary_tool", "qa_tool", "rag_tool");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Set<String> allowedToolNames;

    @Autowired
    public LlmToolSelectionParser(ToolRegistry toolRegistry) {
        this(toolRegistry.getToolNames());
    }

    LlmToolSelectionParser(Set<String> allowedToolNames) {
        if (allowedToolNames == null || allowedToolNames.isEmpty()) {
            throw new IllegalArgumentException("allowedToolNames must not be empty");
        }
        this.allowedToolNames = Set.copyOf(allowedToolNames);
    }

    public LlmToolSelectionResult parse(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            throw new IllegalArgumentException("LLM tool selection response is blank");
        }

        JsonNode root = parseJsonObject(extractFirstJsonObject(rawText));
        String decision = requireAllowedDecision(root.path("decision").asText(""));
        List<String> toolNames = parseToolNames(root.path("toolNames"));
        String routingReason = root.path("routingReason").asText("");
        List<String> matchedKeywords = parseStringArray(root.path("matchedKeywords"), "matchedKeywords", false);
        double confidence = parseConfidence(root.path("confidence"));

        return new LlmToolSelectionResult(decision, toolNames, routingReason, matchedKeywords, confidence);
    }

    private JsonNode parseJsonObject(String jsonObjectText) {
        try {
            JsonNode root = objectMapper.readTree(jsonObjectText);
            if (!root.isObject()) {
                throw new IllegalArgumentException("LLM tool selection JSON must be an object");
            }
            return root;
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Failed to parse LLM tool selection JSON", ex);
        }
    }

    private String extractFirstJsonObject(String rawText) {
        int start = rawText.indexOf('{');
        if (start < 0) {
            throw new IllegalArgumentException("LLM tool selection response does not contain a JSON object");
        }

        boolean inString = false;
        boolean escaped = false;
        int depth = 0;
        for (int index = start; index < rawText.length(); index++) {
            char current = rawText.charAt(index);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (current == '\\' && inString) {
                escaped = true;
                continue;
            }
            if (current == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (current == '{') {
                depth++;
            } else if (current == '}') {
                depth--;
                if (depth == 0) {
                    return rawText.substring(start, index + 1);
                }
            }
        }
        throw new IllegalArgumentException("LLM tool selection response has an incomplete JSON object");
    }

    private String requireAllowedDecision(String decision) {
        if (!ALLOWED_DECISIONS.contains(decision)) {
            throw new IllegalArgumentException("Unsupported tool selection decision: " + decision);
        }
        return decision;
    }

    private List<String> parseToolNames(JsonNode node) {
        List<String> toolNames = parseStringArray(node, "toolNames", true);
        for (String toolName : toolNames) {
            if (!allowedToolNames.contains(toolName)) {
                throw new IllegalArgumentException("Unknown toolName in LLM tool selection: " + toolName);
            }
        }
        return toolNames;
    }

    private List<String> parseStringArray(JsonNode node, String fieldName, boolean requireNonEmpty) {
        if (!node.isArray()) {
            throw new IllegalArgumentException(fieldName + " must be an array");
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            if (!item.isTextual() || item.asText().isBlank()) {
                throw new IllegalArgumentException(fieldName + " must contain only non-blank strings");
            }
            values.add(item.asText().trim());
        }
        if (requireNonEmpty && values.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be empty");
        }
        return List.copyOf(new LinkedHashSet<>(values));
    }

    private double parseConfidence(JsonNode node) {
        if (!node.isNumber()) {
            throw new IllegalArgumentException("confidence must be a number");
        }
        double confidence = node.asDouble();
        if (confidence < 0.0d || confidence > 1.0d) {
            throw new IllegalArgumentException("confidence must be between 0.0 and 1.0");
        }
        return confidence;
    }
}
