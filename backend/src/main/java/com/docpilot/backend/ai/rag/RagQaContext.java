package com.docpilot.backend.ai.rag;

import java.util.List;

public record RagQaContext(
        boolean used,
        String contextText,
        List<RagCitation> citations,
        int chunkCount,
        int retrievedCount
) {

    public RagQaContext {
        contextText = contextText == null ? "" : contextText;
        citations = citations == null ? List.of() : List.copyOf(citations);
        if (chunkCount < 0) {
            throw new IllegalArgumentException("chunkCount must be non-negative");
        }
        if (retrievedCount < 0) {
            throw new IllegalArgumentException("retrievedCount must be non-negative");
        }
    }

    public static RagQaContext empty() {
        return new RagQaContext(false, "", List.of(), 0, 0);
    }
}
