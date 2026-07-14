package com.docpilot.backend.ai.rag.fusion;

import com.docpilot.backend.ai.rag.keyword.KeywordSearchHit;
import com.docpilot.backend.ai.rag.vector.VectorSearchHit;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ReciprocalRankFusionTest {

    @Test
    void shouldFuseVectorAndKeywordResults() {
        ReciprocalRankFusion rrf = new ReciprocalRankFusion();

        List<VectorSearchHit> vectorHits = List.of(
                createVectorHit(1L, 101L, 0.95, "chunk content A"),
                createVectorHit(2L, 101L, 0.85, "chunk content B"),
                createVectorHit(3L, 102L, 0.80, "chunk content C")
        );

        List<KeywordSearchHit> keywordHits = List.of(
                new KeywordSearchHit(3L, 102L, 1L, 2, "chunk content C", 5.2),
                new KeywordSearchHit(1L, 101L, 1L, 0, "chunk content A", 4.8),
                new KeywordSearchHit(4L, 102L, 1L, 3, "chunk content D", 3.5)
        );

        List<FusedSearchHit> fused = rrf.fuse(vectorHits, keywordHits);

        assertFalse(fused.isEmpty());
        assertEquals(4, fused.size()); // 3 vector + 1 keyword-only

        // Check fused scores are calculated
        for (FusedSearchHit hit : fused) {
            assertTrue(hit.fusedScore() > 0);
        }
    }

    @Test
    void shouldBoostHitsAppearingInBothSearches() {
        ReciprocalRankFusion rrf = new ReciprocalRankFusion();

        // Chunk 1 appears in both vector and keyword (should be boosted)
        List<VectorSearchHit> vectorHits = List.of(
                createVectorHit(1L, 101L, 0.95, "matching chunk"),
                createVectorHit(2L, 101L, 0.85, "other chunk")
        );

        List<KeywordSearchHit> keywordHits = List.of(
                new KeywordSearchHit(1L, 101L, 1L, 0, "matching chunk", 5.0)
        );

        List<FusedSearchHit> fused = rrf.fuse(vectorHits, keywordHits);

        assertEquals(2, fused.size());

        // Chunk 1 should have highest fused score (appears in both)
        FusedSearchHit topHit = fused.get(0);
        assertEquals(1L, topHit.chunkId());
        assertTrue(topHit.vectorScore() > 0);
        assertTrue(topHit.keywordScore() > 0);
    }

    @Test
    void shouldHandleVectorOnlyResults() {
        ReciprocalRankFusion rrf = new ReciprocalRankFusion();

        List<VectorSearchHit> vectorHits = List.of(
                createVectorHit(1L, 101L, 0.95, "content")
        );
        List<KeywordSearchHit> keywordHits = List.of();

        List<FusedSearchHit> fused = rrf.fuse(vectorHits, keywordHits);

        assertEquals(1, fused.size());
        assertEquals(1L, fused.get(0).chunkId());
        assertEquals(0.0, fused.get(0).keywordScore());
    }

    @Test
    void shouldHandleKeywordOnlyResults() {
        ReciprocalRankFusion rrf = new ReciprocalRankFusion();

        List<VectorSearchHit> vectorHits = List.of();
        List<KeywordSearchHit> keywordHits = List.of(
                new KeywordSearchHit(1L, 101L, 1L, 0, "content", 5.0)
        );

        List<FusedSearchHit> fused = rrf.fuse(vectorHits, keywordHits);

        assertEquals(1, fused.size());
        assertEquals(1L, fused.get(0).chunkId());
        assertEquals(0.0, fused.get(0).vectorScore());
        assertNull(fused.get(0).vectorId());
    }

    @Test
    void shouldCalculateRRFScoreCorrectly() {
        ReciprocalRankFusion rrf = new ReciprocalRankFusion(60);

        // Rank 1 in vector: RRF = 1/(60+1) = 0.0164
        List<VectorSearchHit> vectorHits = List.of(
                createVectorHit(1L, 101L, 0.95, "content")
        );

        // Rank 1 in keyword: RRF = 1/(60+1) = 0.0164
        // Total = 0.0164 + 0.0164 = 0.0328
        List<KeywordSearchHit> keywordHits = List.of(
                new KeywordSearchHit(1L, 101L, 1L, 0, "content", 5.0)
        );

        List<FusedSearchHit> fused = rrf.fuse(vectorHits, keywordHits);

        assertEquals(1, fused.size());
        double expectedScore = 2.0 / (60.0 + 1.0);
        assertEquals(expectedScore, fused.get(0).fusedScore(), 0.0001);
    }

    @Test
    void shouldSortByFusedScoreDescending() {
        ReciprocalRankFusion rrf = new ReciprocalRankFusion();

        List<VectorSearchHit> vectorHits = List.of(
                createVectorHit(1L, 101L, 0.95, "A"),
                createVectorHit(2L, 101L, 0.85, "B"),
                createVectorHit(3L, 102L, 0.80, "C")
        );

        List<KeywordSearchHit> keywordHits = List.of(
                new KeywordSearchHit(2L, 101L, 1L, 1, "B", 6.0), // Boost chunk 2
                new KeywordSearchHit(3L, 102L, 1L, 2, "C", 5.0)
        );

        List<FusedSearchHit> fused = rrf.fuse(vectorHits, keywordHits);

        // Check descending order
        for (int i = 0; i < fused.size() - 1; i++) {
            assertTrue(fused.get(i).fusedScore() >= fused.get(i + 1).fusedScore());
        }
    }

    private VectorSearchHit createVectorHit(Long chunkId, Long documentId, double score, String content) {
        return new VectorSearchHit(
                "vec_" + chunkId,
                score,
                1L, // userId
                documentId,
                1, // indexVersion
                0, // chunkIndex
                content,
                "hash_" + chunkId, // contentHash
                Map.of("chunkId", chunkId) // payload with chunkId
        );
    }
}
