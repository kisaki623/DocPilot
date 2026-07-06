package com.docpilot.backend.ai.service;

import com.docpilot.backend.document.parser.ParseResult;

public interface RagIndexingTriggerService {

    void triggerAfterParseSuccess(Long userId, Long documentId, String parsedText);

    default void triggerAfterParseSuccess(Long userId, Long documentId, ParseResult parseResult) {
        triggerAfterParseSuccess(userId, documentId, parseResult == null ? "" : parseResult.fullText());
    }
}
