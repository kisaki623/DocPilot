package com.docpilot.backend.ai.service;

import com.docpilot.backend.ai.rag.RagIndexingResult;
import com.docpilot.backend.document.parser.ParseResult;

public interface RagIndexingTriggerService {

    void triggerAfterParseSuccess(Long userId, Long documentId, String parsedText);

    /**
     * Builds the index in the caller thread so a document pipeline can mark
     * parsing successful only after retrieval data is ready.
     */
    RagIndexingResult indexAfterParse(Long userId, Long documentId, ParseResult parseResult);

    default void triggerAfterParseSuccess(Long userId, Long documentId, ParseResult parseResult) {
        triggerAfterParseSuccess(userId, documentId, parseResult == null ? "" : parseResult.fullText());
    }
}
