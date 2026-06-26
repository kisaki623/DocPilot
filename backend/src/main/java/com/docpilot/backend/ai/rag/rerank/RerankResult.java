package com.docpilot.backend.ai.rag.rerank;

import java.util.List;

/**
 * Result of reranking operation.
 */
public record RerankResult(
        List<RerankHit> hits,
        String model
) {
    public RerankResult {
        if (hits == null) {
            throw new IllegalArgumentException("hits must not be null");
        }
        hits = List.copyOf(hits);
    }

    /**
     * Represents a reranked document with its relevance score.
     *
     * @param index          the original index in the input documents list
     * @param relevanceScore the relevance score (higher is more relevant)
     */
    public record RerankHit(
            int index,
            double relevanceScore
    ) {
        public RerankHit {
            if (index < 0) {
                throw new IllegalArgumentException("index must be non-negative");
            }
            if (!Double.isFinite(relevanceScore)) {
                throw new IllegalArgumentException("relevanceScore must be finite");
            }
        }
    }
}
