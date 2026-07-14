package com.docpilot.backend.common;

import com.docpilot.backend.auth.dto.RegisterRequest;
import com.docpilot.backend.common.api.ApiResponse;
import com.docpilot.backend.common.error.ErrorCode;
import com.docpilot.backend.common.exception.BusinessException;
import com.docpilot.backend.common.exception.GlobalExceptionHandler;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
    void shouldHandleBindExceptionAsBadRequest() {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new RegisterRequest(), "request");
        bindingResult.rejectValue("username", "invalid", "username format is invalid");
        BindException exception = new BindException(bindingResult);

        ApiResponse<Void> response = globalExceptionHandler.handleBindException(exception);

        assertEquals(ErrorCode.BAD_REQUEST.getCode(), response.code());
        assertEquals("username format is invalid", response.message());
    }

    @Test
    void shouldHandleConstraintViolationExceptionAsBadRequest() {
        ConstraintViolation<?> violation = mock(ConstraintViolation.class);
        when(violation.getMessage()).thenReturn("documentId must not be null");
        ConstraintViolationException exception = new ConstraintViolationException(java.util.Set.of(violation));

        ApiResponse<Void> response = globalExceptionHandler.handleConstraintViolationException(exception);

        assertEquals(ErrorCode.BAD_REQUEST.getCode(), response.code());
        assertEquals("documentId must not be null", response.message());
    }

    @Test
    void shouldHandleUnexpectedExceptionAsInternalError() {
        ApiResponse<Void> response = globalExceptionHandler.handleException(new RuntimeException("unexpected"));

        assertEquals(ErrorCode.INTERNAL_SERVER_ERROR.getCode(), response.code());
        assertEquals(ErrorCode.INTERNAL_SERVER_ERROR.getMessage(), response.message());
    }
}


