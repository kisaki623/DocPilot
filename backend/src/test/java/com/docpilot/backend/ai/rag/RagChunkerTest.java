package com.docpilot.backend.ai.rag;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RagChunkerTest {

    @Test
    void shouldCreateSingleChunkForShortText() {
        RagChunker chunker = new RagChunker(new RagChunkingPolicy(100, 10, 10));

        RagChunker.RagChunkingResult result = chunker.chunk(61L, "v1", "Redis cache evidence.");

        assertThat(result.truncated()).isFalse();
        assertThat(result.chunks()).hasSize(1);
        DocumentChunk chunk = result.chunks().get(0);
        assertThat(chunk.chunkIndex()).isZero();
        assertThat(chunk.metadata()).containsEntry("documentId", "61");
        assertThat(chunk.metadata()).containsEntry("documentVersion", "v1");
        assertThat(chunk.metadata()).containsEntry("startOffset", "0");
        assertThat(chunk.metadata()).containsEntry("endOffset", String.valueOf(chunk.text().length()));
        assertThat(chunk.metadata().get("contentHash")).isNotBlank();
        assertThat(chunk.metadata().get("chunkId")).startsWith("chunk_");
    }

    @Test
    void shouldCreateMultipleChunksWithOverlap() {
        RagChunker chunker = new RagChunker(new RagChunkingPolicy(10, 3, 10));

        List<DocumentChunk> chunks = chunker.chunk(61L, "v1", "0123456789ABCDEFGHIJ").chunks();

        assertThat(chunks).hasSize(3);
        assertThat(chunks.get(0).text()).isEqualTo("0123456789");
        assertThat(chunks.get(1).text()).isEqualTo("789ABCDEFG");
        assertThat(chunks.get(2).text()).isEqualTo("EFGHIJ");
        assertThat(chunks).extracting(DocumentChunk::chunkIndex).containsExactly(0, 1, 2);
        assertThat(chunks.get(1).metadata()).containsEntry("startOffset", "7");
    }

    @Test
    void shouldGenerateStableChunkIds() {
        RagChunker chunker = new RagChunker(new RagChunkingPolicy(10, 2, 10));

        List<DocumentChunk> first = chunker.chunk(61L, "v1", "0123456789ABCDEFGHIJ").chunks();
        List<DocumentChunk> second = chunker.chunk(61L, "v1", "0123456789ABCDEFGHIJ").chunks();

        assertThat(first).hasSameSizeAs(second);
        for (int i = 0; i < first.size(); i++) {
            assertThat(first.get(i).metadata().get("chunkId"))
                    .isEqualTo(second.get(i).metadata().get("chunkId"));
        }
    }

    @Test
    void shouldReturnEmptyChunksForBlankText() {
        RagChunker chunker = new RagChunker(new RagChunkingPolicy(10, 2, 10));

        RagChunker.RagChunkingResult result = chunker.chunk(61L, "v1", "   ");

        assertThat(result.chunks()).isEmpty();
        assertThat(result.truncated()).isFalse();
    }

    @Test
    void shouldRespectMaxChunksPerDocumentAndMarkTruncated() {
        RagChunker chunker = new RagChunker(new RagChunkingPolicy(5, 0, 2));

        RagChunker.RagChunkingResult result = chunker.chunk(61L, "v1", "0123456789ABCDEFGHIJ");

        assertThat(result.truncated()).isTrue();
        assertThat(result.chunks()).hasSize(2);
        assertThat(result.chunks())
                .extracting(chunk -> chunk.metadata().get("indexTruncated"))
                .containsOnly("true");
    }

    @Test
    void shouldKeepDocumentMetadataIsolated() {
        RagChunker chunker = new RagChunker(new RagChunkingPolicy(20, 5, 10));

        Map<String, String> firstMetadata = chunker.chunk(61L, "v1", "Redis cache evidence.").chunks().get(0).metadata();
        Map<String, String> secondMetadata = chunker.chunk(62L, "v1", "Redis cache evidence.").chunks().get(0).metadata();

        assertThat(firstMetadata).containsEntry("documentId", "61");
        assertThat(secondMetadata).containsEntry("documentId", "62");
        assertThat(firstMetadata.get("chunkId")).isNotEqualTo(secondMetadata.get("chunkId"));
    }

    @Test
    void shouldRejectInvalidPolicy() {
        assertThatThrownBy(() -> new RagChunkingPolicy(10, 10, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("overlapChars");
        assertThatThrownBy(() -> new RagChunkingPolicy(10, 0, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxChunksPerDocument");
    }
}
