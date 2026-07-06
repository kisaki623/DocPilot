package com.docpilot.backend.ai.service.impl;

import com.docpilot.backend.ai.rag.RagIndexingRequest;
import com.docpilot.backend.ai.rag.RagIndexingResult;
import com.docpilot.backend.ai.rag.RagSourceBlock;
import com.docpilot.backend.ai.service.RagIndexingService;
import com.docpilot.backend.ai.service.RagIndexingTriggerService;
import com.docpilot.backend.ai.service.RagScopeGuard;
import com.docpilot.backend.document.parser.DocumentBlock;
import com.docpilot.backend.document.parser.ParseResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

@Service
public class RagIndexingTriggerServiceImpl implements RagIndexingTriggerService {

    static final int DEFAULT_INDEX_VERSION = 1;

    private static final Logger log = LoggerFactory.getLogger(RagIndexingTriggerServiceImpl.class);

    private final RagIndexingService ragIndexingService;
    private final RagScopeGuard ragScopeGuard;
    private final Executor executor;

    public RagIndexingTriggerServiceImpl(RagIndexingService ragIndexingService) {
        this(ragIndexingService, null, ForkJoinPool.commonPool());
    }

    RagIndexingTriggerServiceImpl(RagIndexingService ragIndexingService, Executor executor) {
        this(ragIndexingService, null, executor);
    }

    @Autowired
    public RagIndexingTriggerServiceImpl(RagIndexingService ragIndexingService,
                                         RagScopeGuard ragScopeGuard) {
        this(ragIndexingService, ragScopeGuard, ForkJoinPool.commonPool());
    }

    RagIndexingTriggerServiceImpl(RagIndexingService ragIndexingService,
                                  RagScopeGuard ragScopeGuard,
                                  Executor executor) {
        this.ragIndexingService = ragIndexingService;
        this.ragScopeGuard = ragScopeGuard;
        this.executor = executor == null ? ForkJoinPool.commonPool() : executor;
    }

    @Override
    public void triggerAfterParseSuccess(Long userId, Long documentId, String parsedText) {
        triggerAfterParseSuccess(userId, documentId, parsedText, List.of());
    }

    @Override
    public void triggerAfterParseSuccess(Long userId, Long documentId, ParseResult parseResult) {
        triggerAfterParseSuccess(
                userId,
                documentId,
                parseResult == null ? "" : parseResult.fullText(),
                sourceBlocks(parseResult)
        );
    }

    private void triggerAfterParseSuccess(Long userId,
                                          Long documentId,
                                          String parsedText,
                                          List<RagSourceBlock> sourceBlocks) {
        if (userId == null || documentId == null) {
            log.warn("Skip RAG indexing trigger because userId or documentId is missing. userId={}, documentId={}",
                    userId, documentId);
            return;
        }
        try {
            CompletableFuture.runAsync(() -> indexSafely(userId, documentId, parsedText, sourceBlocks), executor);
        } catch (RuntimeException ex) {
            log.warn("Failed to schedule RAG indexing trigger. userId={}, documentId={}, errorType={}",
                    userId, documentId, ex.getClass().getSimpleName());
        }
    }

    private void indexSafely(Long userId, Long documentId, String parsedText, List<RagSourceBlock> sourceBlocks) {
        try {
            if (ragScopeGuard != null) {
                ragScopeGuard.requireOwnedDocument(userId, documentId);
            }
            RagIndexingResult result = ragIndexingService.index(new RagIndexingRequest(
                    documentId,
                    userId,
                    parsedText,
                    DEFAULT_INDEX_VERSION,
                    "",
                    sourceBlocks
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

    private List<RagSourceBlock> sourceBlocks(ParseResult parseResult) {
        if (parseResult == null || parseResult.blocks().isEmpty()) {
            return List.of();
        }
        return parseResult.blocks().stream()
                .map(this::sourceBlock)
                .toList();
    }

    private RagSourceBlock sourceBlock(DocumentBlock block) {
        return new RagSourceBlock(
                block.blockIndex(),
                block.blockType().name(),
                block.pageNumber(),
                block.sectionTitle(),
                block.sectionPath(),
                block.startOffset(),
                block.endOffset(),
                block.sourceLocator()
        );
    }
}
