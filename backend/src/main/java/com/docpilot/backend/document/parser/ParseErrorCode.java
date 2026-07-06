package com.docpilot.backend.document.parser;

public enum ParseErrorCode {
    UNSUPPORTED_TYPE,
    EMPTY_CONTENT,
    IO_ERROR,
    CORRUPTED_FILE,
    PARSE_TIMEOUT,
    FILE_TOO_LARGE,
    SECURITY_REJECTED
}
