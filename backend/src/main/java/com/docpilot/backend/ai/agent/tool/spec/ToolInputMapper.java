package com.docpilot.backend.ai.agent.tool.spec;

import com.docpilot.backend.ai.agent.tool.DocumentRagQaTool;
import com.docpilot.backend.ai.agent.tool.DocumentSearchTool;
import com.docpilot.backend.ai.agent.tool.DocumentStatusTool;
import com.docpilot.backend.common.error.ErrorCode;
import com.docpilot.backend.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ToolInputMapper {

    public Object toInput(String toolName, Map<String, Object> arguments) {
        return switch (toolName) {
            case "document_status_tool" -> new DocumentStatusTool.StatusInput(
                    requireLong(arguments, "userId"),
                    requireLong(arguments, "documentId")
            );
            case DocumentRagQaTool.TOOL_NAME -> new DocumentRagQaTool.RagQaInput(
                    requireLong(arguments, "userId"),
                    requireLong(arguments, "documentId"),
                    requireString(arguments, "question"),
                    optionalString(arguments, "sessionId"),
                    optionalInteger(arguments, "topK"),
                    optionalInteger(arguments, "indexVersion")
            );
            case DocumentSearchTool.TOOL_NAME -> new DocumentSearchTool.SearchInput(
                    requireLong(arguments, "userId"),
                    requireLong(arguments, "documentId"),
                    requireString(arguments, "query"),
                    optionalInteger(arguments, "topK"),
                    optionalInteger(arguments, "indexVersion")
            );
            default -> throw new BusinessException(ErrorCode.BAD_REQUEST, "Unsupported tool call: " + toolName);
        };
    }

    private Long requireLong(Map<String, Object> arguments, String fieldName) {
        Object value = arguments.get(fieldName);
        if (value instanceof Long longValue) {
            return longValue;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        throw new BusinessException(ErrorCode.BAD_REQUEST, fieldName + " must be a long");
    }

    private Integer optionalInteger(Map<String, Object> arguments, String fieldName) {
        Object value = arguments.get(fieldName);
        if (value == null) {
            return null;
        }
        if (value instanceof Integer integerValue) {
            return integerValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        throw new BusinessException(ErrorCode.BAD_REQUEST, fieldName + " must be an integer");
    }

    private String requireString(Map<String, Object> arguments, String fieldName) {
        String value = optionalString(arguments, fieldName);
        if (value.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, fieldName + " must not be blank");
        }
        return value;
    }

    private String optionalString(Map<String, Object> arguments, String fieldName) {
        Object value = arguments.get(fieldName);
        if (value == null) {
            return "";
        }
        if (value instanceof String text) {
            return text;
        }
        throw new BusinessException(ErrorCode.BAD_REQUEST, fieldName + " must be a string");
    }
}
