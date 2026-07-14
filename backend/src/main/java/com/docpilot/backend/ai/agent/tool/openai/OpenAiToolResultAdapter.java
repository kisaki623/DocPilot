package com.docpilot.backend.ai.agent.tool.openai;

import com.docpilot.backend.ai.agent.tool.spec.ToolCallResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class OpenAiToolResultAdapter {

    private static final int SAFE_TEXT_LIMIT = 500;

    private final ObjectMapper objectMapper;

    public OpenAiToolResultAdapter() {
        this(new ObjectMapper());
    }

    OpenAiToolResultAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public OpenAiToolMessage toToolMessage(OpenAiParsedToolCall call, ToolCallResult result) {
        if (call == null) {
            throw new IllegalArgumentException("call must not be null");
        }
        if (result == null) {
            throw new IllegalArgumentException("result must not be null");
        }
        return new OpenAiToolMessage(
                "tool",
                call.id(),
                call.toolName(),
                toContent(result)
        );
    }

    private String toContent(ToolCallResult result) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("toolName", result.toolName());
        payload.put("status", result.status().name());
        payload.put("outputSummary", safeText(result.outputSummary()));
        payload.put("result", result.success() ? result.result() : null);
        payload.put("errorType", safeText(result.errorType()));
        payload.put("errorMessage", safeText(result.errorMessage()));
        payload.put("durationMs", result.durationMs());
        payload.put("citations", result.citations());
        payload.put("retrievalHits", result.retrievalHits());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            return "{\"toolName\":\"" + escapeJson(result.toolName()) + "\",\"status\":\"FAILED\",\"errorType\":\"SERIALIZATION_FAILED\"}";
        }
    }

    private String safeText(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String sanitized = value
                .replaceAll("(?i)(https?://|jdbc:|redis://|qdrant://|mysql://)\\S+", "[redacted-uri]")
                .replaceAll("(?i)(api[_-]?key|token|secret|password)\\s*[:=]\\s*\\S+", "$1=[redacted]")
                .replaceAll("(?i)sk-[A-Za-z0-9_-]+", "[redacted-key]");
        if (sanitized.length() > SAFE_TEXT_LIMIT) {
            return sanitized.substring(0, SAFE_TEXT_LIMIT) + "...";
        }
        return sanitized;
    }

    private String escapeJson(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
