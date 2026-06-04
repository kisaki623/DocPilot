package com.docpilot.backend.ai.service.impl;

import com.docpilot.backend.ai.rag.RagIndexingRequest;
import com.docpilot.backend.ai.rag.RagIndexingResult;
import com.docpilot.backend.ai.service.RagIndexingService;
import com.docpilot.backend.ai.service.RagIndexingTriggerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

@Service
public class RagIndexingTriggerServiceImpl implements RagIndexingTriggerService {

    static final int DEFAULT_INDEX_VERSION = 1;

    private static final Logger log = LoggerFactory.getLogger(RagIndexingTriggerServiceImpl.class);

    private final RagIndexingService ragIndexingService;
    private final Executor executor;

    @Autowired
    public RagIndexingTriggerServiceImpl(RagIndexingService ragIndexingService) {
        this(ragIndexingService, ForkJoinPool.commonPool());
    }

    RagIndexingTriggerServiceImpl(RagIndexingService ragIndexingService, Executor executor) {
        this.ragIndexingService = ragIndexingService;
        this.executor = executor == null ? ForkJoinPool.commonPool() : executor;
    }

    @Override
    public void triggerAfterParseSuccess(Long userId, Long documentId, String parsedText) {
        if (userId == null || documentId == null) {
            log.warn("Skip RAG indexing trigger because userId or documentId is missing. userId={}, documentId={}",
                    userId, documentId);
            return;
        }
        try {
            CompletableFuture.runAsync(() -> indexSafely(userId, documentId, parsedText), executor);
        } catch (RuntimeException ex) {
            log.warn("Failed to schedule RAG indexing trigger. userId={}, documentId={}, errorType={}",
                    userId, documentId, ex.getClass().getSimpleName());
        }
    }

    private void indexSafely(Long userId, Long documentId, String parsedText) {
        try {
            RagIndexingResult result = ragIndexingService.index(new RagIndexingRequest(
                    documentId,
                    userId,
                    parsedText,
                    DEFAULT_INDEX_VERSION,
                    ""
            ));
            if (result == null) {
                log.warn("RAG indexing trigger finished without result. userId={}, documentId={}, indexVersion={}",
                        userId, documentId, DEFAULT_INDEX_VERSION);
                return;
            }
            log.info("RAG indexing trigger finished. userId={}, documentId={}, indexVersion={}, status={}, chunks={}, vectors={}",
                    result.userId(),
                    result.documentId(),
                    result.indexVersion(),
                    result.status(),
                    result.chunkCount(),
                    result.vectorCount());
        } catch (RuntimeException ex) {
            log.warn("RAG indexing trigger failed. userId={}, documentId={}, indexVersion={}, errorType={}",
                    userId, documentId, DEFAULT_INDEX_VERSION, ex.getClass().getSimpleName());
        }
    }
}
