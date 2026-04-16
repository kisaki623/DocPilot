package com.docpilot.backend.common;

import com.docpilot.backend.common.api.ApiResponse;
import com.docpilot.backend.common.error.ErrorCode;
import com.docpilot.backend.common.exception.BusinessException;
import com.docpilot.backend.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerWebMvcTest {

    private final GlobalExceptionHandler globalExceptionHandler = new GlobalExceptionHandler();

    @Test
    void shouldBuildUnifiedSuccessResponse() {
        ApiResponse<String> response = ApiResponse.success("ok");

        assertEquals(ErrorCode.SUCCESS.getCode(), response.code());
        assertEquals(ErrorCode.SUCCESS.getMessage(), response.message());
        assertEquals("ok", response.data());
    }

    @Test
    void shouldHandleBusinessExceptionWithDefinedCode() {
        ApiResponse<Void> response = globalExceptionHandler
                .handleBusinessException(new BusinessException(ErrorCode.BAD_REQUEST, "invalid request"));

        assertEquals(ErrorCode.BAD_REQUEST.getCode(), response.code());
        assertEquals("invalid request", response.message());
    }

    @Test
    void shouldHandleUnexpectedExceptionAsInternalError() {
        ApiResponse<Void> response = globalExceptionHandler.handleException(new RuntimeException("unexpected"));

        assertEquals(ErrorCode.INTERNAL_SERVER_ERROR.getCode(), response.code());
        assertEquals(ErrorCode.INTERNAL_SERVER_ERROR.getMessage(), response.message());
    }
}


