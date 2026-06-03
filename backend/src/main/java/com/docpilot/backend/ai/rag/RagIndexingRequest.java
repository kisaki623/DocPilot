package com.docpilot.backend.ai.rag;

public record RagIndexingRequest(
        Long documentId,
        Long userId,
        String text,
        Integer indexVersion,
        String embeddingModel
) {

    public RagIndexingRequest {
        text = text == null ? "" : text;
        embeddingModel = embeddingModel == null ? "" : embeddingModel.trim();
    }
}
