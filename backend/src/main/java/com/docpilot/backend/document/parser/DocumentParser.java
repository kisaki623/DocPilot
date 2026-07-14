package com.docpilot.backend.document.parser;

public interface DocumentParser {

    String parserName();

    String parserVersion();

    boolean supports(ParserInput input);

    ParseResult parse(ParserInput input);
}
