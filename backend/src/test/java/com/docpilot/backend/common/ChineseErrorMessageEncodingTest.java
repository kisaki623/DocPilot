package com.docpilot.backend.common;

import com.docpilot.backend.ai.agent.tool.spec.ToolArgumentValidator;
import com.docpilot.backend.common.context.UserHolder;
import com.docpilot.backend.common.error.ErrorCode;
import com.docpilot.backend.common.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChineseErrorMessageEncodingTest {

    @AfterEach
    void tearDown() {
        UserHolder.clear();
    }

    @Test
    void shouldKeepErrorCodeMessagesReadable() {
        assertReadableChinese(ErrorCode.SUCCESS.getMessage());
        assertReadableChinese(ErrorCode.FORBIDDEN.getMessage());
        assertReadableChinese(ErrorCode.KNOWLEDGE_BASE_FORBIDDEN.getMessage());

        assertEquals("成功", ErrorCode.SUCCESS.getMessage());
        assertEquals("无权限访问", ErrorCode.FORBIDDEN.getMessage());
        assertEquals("无权访问该知识库", ErrorCode.KNOWLEDGE_BASE_FORBIDDEN.getMessage());
    }

    @Test
    void shouldKeepUserHolderLoginMessageReadable() {
        BusinessException ex = assertThrows(BusinessException.class, UserHolder::requireUserId);

        assertEquals(ErrorCode.UNAUTHORIZED, ex.getErrorCode());
        assertEquals("用户未登录", ex.getMessage());
        assertReadableChinese(ex.getMessage());
    }

    @Test
    void shouldKeepToolArgumentValidatorLoginMessageReadable() {
        ToolArgumentValidator validator = new ToolArgumentValidator();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> validator.validate(null, null, null));

        assertEquals(ErrorCode.UNAUTHORIZED, ex.getErrorCode());
        assertEquals("用户未登录", ex.getMessage());
        assertReadableChinese(ex.getMessage());
    }

    private void assertReadableChinese(String message) {
        assertFalse(message.matches(".*(锛|鏂|銆|闈|�|鎴|璇|鏈|鏃|鐢|涓|瑙|楠|绋|鐭|搴).*"),
                "message contains mojibake: " + message);
    }
}
