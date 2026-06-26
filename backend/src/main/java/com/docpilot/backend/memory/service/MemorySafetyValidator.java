package com.docpilot.backend.memory.service;

import com.docpilot.backend.common.error.ErrorCode;
import com.docpilot.backend.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class MemorySafetyValidator {

    private static final List<String> SENSITIVE_PATTERNS = List.of(
            "api_key",
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

    public void validate(String content) {
        String normalized = content == null ? "" : content.toLowerCase(Locale.ROOT);
        for (String pattern : SENSITIVE_PATTERNS) {
            if (normalized.contains(pattern)) {
                throw new BusinessException(ErrorCode.MEMORY_SENSITIVE_CONTENT_REJECTED);
            }
        }
    }
}
