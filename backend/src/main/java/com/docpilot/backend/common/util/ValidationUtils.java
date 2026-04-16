package com.docpilot.backend.common.util;

import com.docpilot.backend.common.error.ErrorCode;
import com.docpilot.backend.common.exception.BusinessException;

public final class ValidationUtils {

    private ValidationUtils() {
    }

    public static void requireNonNull(Object value, String fieldName) {
        if (value == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, fieldName + " 不能为空");
        }
    }

    public static void requireNonBlank(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, fieldName + " 不能为空白");
        }
    }
}

