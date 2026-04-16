package com.docpilot.backend.common;

import com.docpilot.backend.common.error.ErrorCode;
import com.docpilot.backend.common.exception.BusinessException;
import com.docpilot.backend.common.util.ValidationUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ValidationUtilsTest {

    @Test
    void shouldThrowBusinessExceptionWhenValueIsNull() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> ValidationUtils.requireNonNull(null, "docId"));

        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
    }

    @Test
    void shouldThrowBusinessExceptionWhenStringIsBlank() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> ValidationUtils.requireNonBlank("   ", "question"));

        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
    }
}

