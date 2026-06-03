package com.docpilot.backend.ai.rag.vector;

import com.docpilot.backend.ai.entity.DocumentChunkEntity;
import com.docpilot.backend.ai.rag.EmbeddingVector;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class VectorPointTest {

    @Test
    void shouldCreateStablePointFromDocumentChunkEntity() {
        DocumentChunkEntity chunk = chunk("hash-a");

        VectorPoint first = VectorPoint.fromDocumentChunk(chunk, vector());
        VectorPoint second = VectorPoint.fromDocumentChunk(chunk, vector());

        assertThat(first.id()).isEqualTo(second.id());
        assertThat(first.userId()).isEqualTo(7L);
        assertThat(first.documentId()).isEqualTo(61L);
        assertThat(first.indexVersion()).isEqualTo(1);
        assertThat(first.chunkIndex()).isEqualTo(0);
        assertThat(first.metadata())
                .containsEntry("chunkId", 10L)
                .containsEntry("startOffset", 3)
                .containsEntry("endOffset", 18)
                .containsEntry("tokenCount", 15)
                .containsEntry("embeddingModel", "mock-model");
    }

    @Test
    void shouldChangeStableIdWhenContentHashChanges() {
        VectorPoint first = VectorPoint.fromDocumentChunk(chunk("hash-a"), vector());
        VectorPoint second = VectorPoint.fromDocumentChunk(chunk("hash-b"), vector());

        assertThat(first.id()).isNotEqualTo(second.id());
    }

    @Test
    void shouldNotAllowMetadataToOverrideCorePayloadFields() {
        VectorPoint point = new VectorPoint("550e8400-e29b-41d4-a716-446655440000",
                7L, 61L, 1, 0, "chunk content", "hash-a", vector(),
                Map.of("userId", 99L, "documentId", 100L, "indexVersion", 2));

        assertThat(point.payload())
                .containsEntry("userId", 7L)
                .containsEntry("documentId", 61L)
                .containsEntry("indexVersion", 1);
    }

    private DocumentChunkEntity chunk(String contentHash) {
        DocumentChunkEntity chunk = new DocumentChunkEntity();
        chunk.setId(10L);
        chunk.setUserId(7L);
        chunk.setDocumentId(61L);
        chunk.setIndexVersion(1);
        chunk.setChunkIndex(0);
        chunk.setContent("chunk content");
        chunk.setContentHash(contentHash);
        chunk.setStartOffset(3);
        chunk.setEndOffset(18);
        chunk.setTokenCount(15);
        chunk.setEmbeddingModel("mock-model");
        return chunk;
    }

    private EmbeddingVector vector() {
        return new EmbeddingVector(List.of(0.1D, 0.2D));
    }
}
