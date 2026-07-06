package com.docpilot.backend.ai.rag;

import java.util.List;

public record RagIndexingRequest(
        Long documentId,
        Long userId,
        String text,
        Integer indexVersion,
        String embeddingModel,
        List<RagSourceBlock> sourceBlocks
) {

    public RagIndexingRequest(Long documentId,
                              Long userId,
                              String text,
                              Integer indexVersion,
                              String embeddingModel) {
        this(documentId, userId, text, indexVersion, embeddingModel, List.of());
    }

    public RagIndexingRequest {
        text = text == null ? "" : text;
        embeddingModel = embeddingModel == null ? "" : embeddingModel.trim();
        sourceBlocks = sourceBlocks == null ? List.of() : List.copyOf(sourceBlocks);
    }
}
