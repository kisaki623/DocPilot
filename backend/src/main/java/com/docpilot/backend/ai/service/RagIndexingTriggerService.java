package com.docpilot.backend.ai.service;

public interface RagIndexingTriggerService {

    void triggerAfterParseSuccess(Long userId, Long documentId, String parsedText);
}
