package com.docpilot.backend.ai.rag;

import com.docpilot.backend.ai.vo.KnowledgeBaseRagCitationResponse;
import com.docpilot.backend.ai.vo.KnowledgeBaseRagRetrievalHitResponse;
import com.docpilot.backend.ai.vo.RagCitationResponse;
import com.docpilot.backend.ai.vo.RagRetrievalHitResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RagEvidenceQuoteExtractorTest {

    @Test
    void shouldExposeQuoteLevelCitationForSingleDocumentRag() {
        String content = "Intro sentence. The approval owner is real-alpha-approval-marker and must sign. Extra note.";
        String expectedQuote = "The approval owner is real-alpha-approval-marker and must sign.";
        int quoteStart = content.indexOf(expectedQuote);
        RagRetrievalHit hit = new RagRetrievalHit(
                1,
                "vector-1",
                0.91D,
                7L,
                101L,
                2,
                501L,
                3,
                content,
                "hash-a",
                100,
                100 + content.length(),
                32,
                "mock-embedding"
        );

        RagEvidenceCitation citation = hit.toCitation();
        RagCitationResponse citationResponse = RagCitationResponse.from(citation);
        RagRetrievalHitResponse hitResponse = RagRetrievalHitResponse.from(hit);

        assertThat(hit.quoteText()).isEqualTo(expectedQuote);
        assertThat(hit.quoteStartOffset()).isEqualTo(100 + quoteStart);
        assertThat(hit.quoteEndOffset()).isEqualTo(100 + quoteStart + expectedQuote.length());
        assertThat(citation.quoteText()).isEqualTo(expectedQuote);
        assertThat(citationResponse.getQuoteText()).isEqualTo(expectedQuote);
        assertThat(hitResponse.getQuoteText()).isEqualTo(expectedQuote);
        assertThat(citationResponse.getQuoteStartOffset()).isEqualTo(hit.quoteStartOffset());
        assertThat(hitResponse.getQuoteEndOffset()).isEqualTo(hit.quoteEndOffset());
    }

    @Test
    void shouldExposeQuoteLevelCitationForKnowledgeBaseRag() {
        String content = "Noise text. Beta policy says real-beta-retention-marker must be archived for seven years. Tail.";
        String expectedQuote = "Beta policy says real-beta-retention-marker must be archived for seven years.";
        int quoteStart = content.indexOf(expectedQuote);
        KnowledgeBaseRagRetrievalHit hit = new KnowledgeBaseRagRetrievalHit(
                2,
                10L,
                "vector-2",
                0.88D,
                7L,
                202L,
                "Beta Handbook",
                4,
                602L,
                5,
                content,
                "hash-b",
                200,
                200 + content.length(),
                30,
                "mock-embedding",
                0.82D,
                0.74D,
                0.90D,
                null
        );

        KnowledgeBaseRagEvidenceCitation citation = hit.toCitation();
        KnowledgeBaseRagCitationResponse citationResponse = KnowledgeBaseRagCitationResponse.from(citation);
        KnowledgeBaseRagRetrievalHitResponse hitResponse = KnowledgeBaseRagRetrievalHitResponse.from(hit);

        assertThat(hit.quoteText()).isEqualTo(expectedQuote);
        assertThat(hit.quoteStartOffset()).isEqualTo(200 + quoteStart);
        assertThat(hit.quoteEndOffset()).isEqualTo(200 + quoteStart + expectedQuote.length());
        assertThat(citation.quoteText()).isEqualTo(expectedQuote);
        assertThat(citationResponse.getQuoteText()).isEqualTo(expectedQuote);
        assertThat(hitResponse.getQuoteText()).isEqualTo(expectedQuote);
        assertThat(citationResponse.getQuoteStartOffset()).isEqualTo(hit.quoteStartOffset());
        assertThat(hitResponse.getQuoteEndOffset()).isEqualTo(hit.quoteEndOffset());
    }

    @Test
    void shouldKeepQuoteOffsetsNullableWhenChunkOffsetsAreMissing() {
        RagRetrievalHit hit = new RagRetrievalHit(
                1,
                "vector-3",
                0.77D,
                7L,
                101L,
                1,
                501L,
                0,
                "Fallback sentence without marker but still readable.",
                "hash-c",
                null,
                null,
                12,
                "mock-embedding"
        );

        assertThat(hit.quoteText()).isEqualTo("Fallback sentence without marker but still readable.");
        assertThat(hit.quoteStartOffset()).isNull();
        assertThat(hit.quoteEndOffset()).isNull();
    }
}
