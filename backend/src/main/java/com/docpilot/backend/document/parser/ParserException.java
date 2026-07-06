package com.docpilot.backend.document.parser;

public class ParserException extends RuntimeException {

    private final ParseErrorCode errorCode;

    public ParserException(ParseErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ParserException(ParseErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ParseErrorCode getErrorCode() {
        return errorCode;
    }
}
