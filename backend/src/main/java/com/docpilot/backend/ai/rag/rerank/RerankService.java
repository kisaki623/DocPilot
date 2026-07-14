package com.docpilot.backend.ai.rag.rerank;

/**
 * Service for reranking documents using Cross-Encoder models.
 * <p>
 * Cross-Encoders jointly encode query and document, providing more accurate
 * relevance scores than separate embeddings, at the cost of higher latency.
 * </p>
 * <p>
 * Typical usage: retrieve many candidates with vector search, then rerank
 * the top candidates with Cross-Encoder for final selection.
 * </p>
 */
public interface RerankService {

    /**
     * Rerank documents against a query using a Cross-Encoder model.
     *
     * @param request the rerank request
     * @return rerank result with hits sorted by relevance score (descending)
     */
    RerankResult rerank(RerankRequest request);
}
