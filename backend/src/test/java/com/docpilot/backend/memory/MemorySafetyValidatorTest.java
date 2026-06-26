package com.docpilot.backend.memory;

import com.docpilot.backend.common.error.ErrorCode;
import com.docpilot.backend.common.exception.BusinessException;
import com.docpilot.backend.memory.service.MemorySafetyValidator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MemorySafetyValidatorTest {

    private final MemorySafetyValidator validator = new MemorySafetyValidator();

    @Test
    void shouldRejectSecrets() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> validator.validate("记住我的 api_key 是 abc"));

        assertEquals(ErrorCode.MEMORY_SENSITIVE_CONTENT_REJECTED, ex.getErrorCode());
    }

    @Test
    void shouldAllowNormalPreference() {
        validator.validate("用户希望回答先给结论，再解释工程取舍");
    }
}
