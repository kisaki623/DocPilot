package com.docpilot.backend.ai.agent.tool.spec;

import com.docpilot.backend.ai.agent.tool.DocumentRagQaTool;
import com.docpilot.backend.common.error.ErrorCode;
import com.docpilot.backend.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class ToolArgumentValidator {

    public static final int MAX_TOP_K = 10;

    public Map<String, Object> validate(Long currentUserId, ToolSpec spec, Map<String, Object> rawArguments) {
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户未登录");
        }
        if (spec == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "tool spec must not be null");
        }
        Map<String, Object> arguments = new LinkedHashMap<>();
        if (rawArguments != null) {
            arguments.putAll(rawArguments);
        }
        injectAndValidateUserId(currentUserId, arguments);

        for (String field : spec.requiredFields()) {
            if (!arguments.containsKey(field) || isBlank(arguments.get(field))) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "required argument missing: " + field);
            }
        }

        Long documentId = optionalLong(arguments, "documentId");
        if (documentId != null) {
            requirePositive(documentId, "documentId");
            arguments.put("documentId", documentId);
        }

        Integer topK = optionalInteger(arguments, "topK");
        if (topK != null) {
            if (topK <= 0) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "topK must be positive");
            }
            arguments.put("topK", Math.min(topK, MAX_TOP_K));
        }

        Integer indexVersion = optionalInteger(arguments, "indexVersion");
        if (indexVersion != null) {
            if (indexVersion <= 0) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "indexVersion must be positive");
            }
            arguments.put("indexVersion", indexVersion);
        }

        normalizeOptionalString(arguments, "sessionId");
        if (DocumentRagQaTool.TOOL_NAME.equals(spec.name())) {
            normalizeRequiredString(arguments, "question");
        }
        normalizeOptionalString(arguments, "task");
        normalizeOptionalString(arguments, "summary");
        normalizeOptionalString(arguments, "content");
        normalizeOptionalString(arguments, "documentText");
        return Collections.unmodifiableMap(new LinkedHashMap<>(arguments));
    }

    private void injectAndValidateUserId(Long currentUserId, Map<String, Object> arguments) {
        Long requestedUserId = optionalLong(arguments, "userId");
        if (requestedUserId != null && !currentUserId.equals(requestedUserId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "userId is outside current scope");
        }
        arguments.put("userId", currentUserId);
    }

    private void normalizeRequiredString(Map<String, Object> arguments, String fieldName) {
        String value = optionalString(arguments, fieldName);
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, fieldName + " must not be blank");
        }
        arguments.put(fieldName, value.trim());
    }

    private void normalizeOptionalString(Map<String, Object> arguments, String fieldName) {
        if (!arguments.containsKey(fieldName)) {
            return;
        }
        String value = optionalString(arguments, fieldName);
        arguments.put(fieldName, value == null ? "" : value.trim());
    }

    private Long optionalLong(Map<String, Object> arguments, String fieldName) {
        if (!arguments.containsKey(fieldName) || arguments.get(fieldName) == null) {
            return null;
        }
        Object value = arguments.get(fieldName);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ex) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, fieldName + " must be a long");
            }
        }
        throw new BusinessException(ErrorCode.BAD_REQUEST, fieldName + " must be a long");
    }

    private Integer optionalInteger(Map<String, Object> arguments, String fieldName) {
        if (!arguments.containsKey(fieldName) || arguments.get(fieldName) == null) {
            return null;
        }
        Object value = arguments.get(fieldName);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ex) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, fieldName + " must be an integer");
            }
        }
        throw new BusinessException(ErrorCode.BAD_REQUEST, fieldName + " must be an integer");
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

    private void requirePositive(Long value, String fieldName) {
        if (value <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, fieldName + " must be positive");
        }
    }

    private boolean isBlank(Object value) {
        return value == null || value instanceof String text && text.isBlank();
    }
}
