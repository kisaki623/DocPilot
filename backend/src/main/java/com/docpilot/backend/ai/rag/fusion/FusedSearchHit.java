package com.docpilot.backend.ai.rag.fusion;

/**
 * Represents a fused search hit combining vector and keyword search results.
 *
 * @param chunkId      the chunk ID
 * @param documentId   the document ID
 * @param userId       the user ID
 * @param indexVersion the chunk index version
 * @param chunkIndex   the chunk index within the document
 * @param content      the chunk content
 * @param contentHash  the chunk content hash
 * @param vectorId     the vector ID (may be null if only from keyword search)
 * @param fusedScore   the RRF fused score
 * @param vectorScore  the original vector similarity score
 * @param keywordScore the original BM25 keyword score
 */
public record FusedSearchHit(
        Long chunkId,
        Long documentId,
        Long userId,
        Integer indexVersion,
        Integer chunkIndex,
        String content,
        String contentHash,
        Integer startOffset,
        Integer endOffset,
        Integer tokenCount,
        String embeddingModel,
        String vectorId,
        double fusedScore,
        double vectorScore,
        double keywordScore
) {
}
