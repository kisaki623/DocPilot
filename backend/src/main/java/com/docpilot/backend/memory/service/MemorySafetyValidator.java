package com.docpilot.backend.memory.service;

import com.docpilot.backend.common.error.ErrorCode;
import com.docpilot.backend.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Component
public class MemorySafetyValidator {

    private static final List<String> SENSITIVE_PATTERNS = List.of(
            "api_key",
            "api key",
            "api-key",
            "apikey",
            "secret",
            "password",
            "passwd",
            "token",
            "bearer ",
            "authorization:",
            "jdbc:",
            ".env",
            "ssh-rsa",
            "private key",
            "access_key",
            "accesskey"
    );

    private static final Pattern SECRET_KEY_SHAPE =
            Pattern.compile("\\bsk-[a-z0-9_-]{8,}\\b", Pattern.CASE_INSENSITIVE);

    public void validate(String content) {
        String normalized = content == null ? "" : content.toLowerCase(Locale.ROOT);
        for (String pattern : SENSITIVE_PATTERNS) {
            if (normalized.contains(pattern)) {
                throw new BusinessException(ErrorCode.MEMORY_SENSITIVE_CONTENT_REJECTED);
            }
        }
        if (SECRET_KEY_SHAPE.matcher(normalized).find()) {
            throw new BusinessException(ErrorCode.MEMORY_SENSITIVE_CONTENT_REJECTED);
        }
    }
}
