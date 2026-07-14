package com.docpilot.backend.ai.agent.tool.openai;

import com.docpilot.backend.common.error.ErrorCode;
import com.docpilot.backend.common.exception.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class OpenAiToolCallParser {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    public OpenAiToolCallParser() {
        this(new ObjectMapper());
    }

    OpenAiToolCallParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<OpenAiParsedToolCall> parse(String responseJson) {
        JsonNode root = parseRoot(responseJson);
        JsonNode toolCalls = root.path("choices").path(0).path("message").path("tool_calls");
        if (!toolCalls.isArray() || toolCalls.isEmpty()) {
            throw badRequest("model response does not contain tool_calls");
        }
        List<OpenAiParsedToolCall> result = new ArrayList<>();
        for (JsonNode toolCall : toolCalls) {
            result.add(parseToolCall(toolCall));
        }
        return List.copyOf(result);
    }

    private OpenAiParsedToolCall parseToolCall(JsonNode toolCall) {
        String id = requiredText(toolCall.path("id"), "tool_call id");
        String type = requiredText(toolCall.path("type"), "tool_call type");
        if (!"function".equals(type)) {
            throw badRequest("unsupported tool_call type");
        }
        JsonNode function = toolCall.path("function");
        String name = requiredText(function.path("name"), "function name");
        String argumentsJson = function.path("arguments").asText("{}");
        Map<String, Object> arguments = parseArguments(argumentsJson);
        return new OpenAiParsedToolCall(id, name, arguments);
    }

    private JsonNode parseRoot(String responseJson) {
        if (responseJson == null || responseJson.isBlank()) {
            throw badRequest("model response is blank");
        }
        try {
            return objectMapper.readTree(responseJson);
        } catch (JsonProcessingException ex) {
            throw badRequest("model response is not valid JSON");
        }
    }

    private Map<String, Object> parseArguments(String argumentsJson) {
        try {
            JsonNode node = objectMapper.readTree(argumentsJson == null || argumentsJson.isBlank() ? "{}" : argumentsJson);
            if (!node.isObject()) {
                throw badRequest("function arguments must be a JSON object");
            }
            return objectMapper.convertValue(node, MAP_TYPE);
        } catch (JsonProcessingException ex) {
            throw badRequest("function arguments are not valid JSON");
        }
    }

    private String requiredText(JsonNode node, String fieldName) {
        if (!node.isTextual() || node.asText().isBlank()) {
            throw badRequest(fieldName + " must not be blank");
        }
        return node.asText().trim();
    }

    private BusinessException badRequest(String message) {
        return new BusinessException(ErrorCode.BAD_REQUEST, message);
    }
}
