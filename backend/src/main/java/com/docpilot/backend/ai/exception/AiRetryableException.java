package com.docpilot.backend.ai.exception;

public class AiRetryableException extends RuntimeException {

    public AiRetryableException(String message) {
        super(message);
    }

    public AiRetryableException(String message, Throwable cause) {
        super(message, cause);
    }
}

