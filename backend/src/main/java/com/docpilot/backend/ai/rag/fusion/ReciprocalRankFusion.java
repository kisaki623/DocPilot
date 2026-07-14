package com.docpilot.backend.ai.rag.fusion;

import com.docpilot.backend.ai.rag.keyword.KeywordSearchHit;
import com.docpilot.backend.ai.rag.vector.VectorSearchHit;

import java.util.*;

/**
 * Reciprocal Rank Fusion (RRF) for combining vector and keyword search results.
 * <p>
 * RRF formula: score(d) = sum 1 / (k + rank(d))
 * where k is a constant (default 60) and rank(d) is the rank of document d in each result list.
 * </p>
 * <p>
 * RRF is simple, effective, and doesn't require score normalization.
 * </p>
 */
public class ReciprocalRankFusion {

    private static final int DEFAULT_K = 60;

    private final int k;

    public ReciprocalRankFusion() {
        this(DEFAULT_K);
    }

    public ReciprocalRankFusion(int k) {
        if (k < 0) {
            throw new IllegalArgumentException("k must be non-negative");
        }
        this.k = k;
    }

    /**
     * Fuse vector and keyword search results using RRF.
     *
     * @param vectorHits  vector search hits (ordered by similarity score)
     * @param keywordHits keyword search hits (ordered by BM25 score)
     * @return fused hits sorted by RRF score (descending)
     */
    public List<FusedSearchHit> fuse(List<VectorSearchHit> vectorHits, List<KeywordSearchHit> keywordHits) {
        Map<String, FusedSearchHit> fusedMap = new HashMap<>();

        // Process vector hits
        for (int i = 0; i < vectorHits.size(); i++) {
            VectorSearchHit hit = vectorHits.get(i);
            int rank = i + 1;
            double rrfScore = 1.0 / (k + rank);

            Long chunkId = extractChunkId(hit);
            String key = makeKey(chunkId, hit.documentId());
            fusedMap.put(key, new FusedSearchHit(
                    chunkId,
                    hit.documentId(),
                    hit.userId(),
                    hit.indexVersion(),
                    hit.chunkIndex(),
                    hit.content(),
                    hit.contentHash(),
                    intValue(hit.payload().get("startOffset")),
                    intValue(hit.payload().get("endOffset")),
                    intValue(hit.payload().get("tokenCount")),
                    stringValue(hit.payload().get("embeddingModel")),
                    hit.id(),
                    rrfScore,
                    hit.score(),
                    0.0
            ));
        }

        // Process keyword hits and merge
        for (int i = 0; i < keywordHits.size(); i++) {
            KeywordSearchHit hit = keywordHits.get(i);
            int rank = i + 1;
            double rrfScore = 1.0 / (k + rank);

            String key = makeKey(hit.getChunkId(), hit.getDocumentId());
            FusedSearchHit existing = fusedMap.get(key);
            if (existing != null) {
                // Merge: add RRF score from keyword search
                fusedMap.put(key, new FusedSearchHit(
                        existing.chunkId(),
                        existing.documentId(),
                        existing.userId(),
                        existing.indexVersion(),
                        existing.chunkIndex(),
                        existing.content(),
                        existing.contentHash(),
                        existing.startOffset(),
                        existing.endOffset(),
                        existing.tokenCount(),
                        existing.embeddingModel(),
                        existing.vectorId(),
                        existing.fusedScore() + rrfScore,
                        existing.vectorScore(),
                        hit.getScore()
                ));
            } else {
                // New hit from keyword search only
                fusedMap.put(key, new FusedSearchHit(
                        hit.getChunkId(),
                        hit.getDocumentId(),
                        hit.getUserId(),
                        hit.getIndexVersion(),
                        hit.getChunkIndex(),
                        hit.getContent(),
                        hit.getContentHash(),
                        hit.getStartOffset(),
                        hit.getEndOffset(),
                        hit.getTokenCount(),
                        hit.getEmbeddingModel(),
                        null,
                        rrfScore,
                        0.0,
                        hit.getScore()
                ));
            }
        }

        // Sort by fused score descending
        List<FusedSearchHit> result = new ArrayList<>(fusedMap.values());
        result.sort((a, b) -> Double.compare(b.fusedScore(), a.fusedScore()));
        return result;
    }

    private String makeKey(Long chunkId, Long documentId) {
        return documentId + ":" + chunkId;
    }

    /**
     * Extract chunkId from VectorSearchHit payload.
     */
    private Long extractChunkId(VectorSearchHit hit) {
        Object value = hit.payload().get("chunkId");
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Integer intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    public int getK() {
        return k;
    }
}
