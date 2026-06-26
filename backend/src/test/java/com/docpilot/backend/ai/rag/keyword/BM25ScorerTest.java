package com.docpilot.backend.ai.rag.keyword;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BM25ScorerTest {

    @Test
    void shouldTokenizeCjkTextAsCharacters() {
        BM25Scorer scorer = new BM25Scorer();
        List<String> tokens = scorer.tokenize("知识库检索");

        assertEquals(5, tokens.size());
        assertTrue(tokens.contains("知"));
        assertTrue(tokens.contains("识"));
        assertTrue(tokens.contains("库"));
        assertTrue(tokens.contains("检"));
        assertTrue(tokens.contains("索"));
    }

    @Test
    void shouldTokenizeEnglishTextAsWords() {
        BM25Scorer scorer = new BM25Scorer();
        List<String> tokens = scorer.tokenize("knowledge base retrieval");

        assertEquals(3, tokens.size());
        assertTrue(tokens.contains("knowledge"));
        assertTrue(tokens.contains("base"));
        assertTrue(tokens.contains("retrieval"));
    }

    @Test
    void shouldCalculateBM25Score() {
        BM25Scorer scorer = new BM25Scorer();

        List<String> corpus = List.of(
                "Redis stores QA answers",
                "Qdrant stores vectors",
                "MySQL stores metadata"
        );
        scorer.initializeCorpus(corpus);

        String query = "stores vectors";
        double score1 = scorer.score(query, "Qdrant stores vectors");
        double score2 = scorer.score(query, "Redis stores QA answers");
        double score3 = scorer.score(query, "MySQL stores metadata");

        // Document with "stores vectors" should score highest
        assertTrue(score1 > score2);
        assertTrue(score1 > score3);

        // All should have positive scores (all contain "stores")
        assertTrue(score1 > 0);
        assertTrue(score2 > 0);
        assertTrue(score3 > 0);
    }

    @Test
    void shouldHandleEmptyQuery() {
        BM25Scorer scorer = new BM25Scorer();
        scorer.initializeCorpus(List.of("test document"));

        assertEquals(0.0, scorer.score("", "test document"));
        assertEquals(0.0, scorer.score(null, "test document"));
    }

    @Test
    void shouldHandleEmptyDocument() {
        BM25Scorer scorer = new BM25Scorer();
        scorer.initializeCorpus(List.of("test document"));

        assertEquals(0.0, scorer.score("query", ""));
        assertEquals(0.0, scorer.score("query", null));
    }

    @Test
    void shouldHandleNoMatchingTerms() {
        BM25Scorer scorer = new BM25Scorer();
        scorer.initializeCorpus(List.of("Redis stores data", "Qdrant stores vectors"));

        double score = scorer.score("knowledge base", "Redis stores data");
        assertEquals(0.0, score);
    }

    @Test
    void shouldHandleCjkQuery() {
        BM25Scorer scorer = new BM25Scorer();

        List<String> corpus = List.of(
                "Redis存储QA答案",
                "Qdrant存储向量",
                "MySQL存储元数据"
        );
        scorer.initializeCorpus(corpus);

        String query = "存储向量";
        double score1 = scorer.score(query, "Qdrant存储向量");
        double score2 = scorer.score(query, "Redis存储QA答案");

        // Exact match should score higher
        assertTrue(score1 > score2);
        assertTrue(score1 > 0);
    }

    @Test
    void shouldRespectK1Parameter() {
        BM25Scorer scorer1 = new BM25Scorer(1.5, 0.75);
        BM25Scorer scorer2 = new BM25Scorer(2.0, 0.75);

        List<String> corpus = List.of("test document");
        scorer1.initializeCorpus(corpus);
        scorer2.initializeCorpus(corpus);

        double score1 = scorer1.score("test", "test test test");
        double score2 = scorer2.score("test", "test test test");

        // Higher k1 means less term frequency saturation
        assertNotEquals(score1, score2);
    }

    @Test
    void shouldCalculateAverageDocLength() {
        BM25Scorer scorer = new BM25Scorer();
        List<String> corpus = List.of(
                "short",
                "medium length doc",
                "very long document with many words"
        );
        scorer.initializeCorpus(corpus);

        assertTrue(scorer.getAvgDocLength() > 0);
    }
}
