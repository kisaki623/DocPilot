package com.docpilot.backend.ai.exception;

public class AiNonRetryableException extends RuntimeException {

    public AiNonRetryableException(String message) {
        super(message);
    }

    public AiNonRetryableException(String message, Throwable cause) {
        super(message, cause);
    }
}

