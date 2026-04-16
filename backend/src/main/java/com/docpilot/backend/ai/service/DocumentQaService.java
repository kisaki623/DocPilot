package com.docpilot.backend.ai.service;

import com.docpilot.backend.ai.vo.DocumentQaResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import com.docpilot.backend.ai.vo.DocumentQaHistoryItemResponse;

public interface DocumentQaService {

    default DocumentQaResponse answer(Long userId, Long documentId, String question) {
        return answer(userId, documentId, question, null);
    }

    DocumentQaResponse answer(Long userId, Long documentId, String question, String sessionId);

    default SseEmitter streamAnswer(Long userId, Long documentId, String question) {
        return streamAnswer(userId, documentId, question, null);
    }

    SseEmitter streamAnswer(Long userId, Long documentId, String question, String sessionId);

    List<DocumentQaHistoryItemResponse> listHistory(Long userId, Long documentId, Integer limit);
}

