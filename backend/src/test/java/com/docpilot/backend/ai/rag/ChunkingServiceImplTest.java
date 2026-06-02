package com.docpilot.backend.ai.rag;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChunkingServiceImplTest {

    private final ChunkingService chunkingService = new ChunkingServiceImpl();

    @Test
    void shouldCreateSingleChunkForShortText() {
        List<DocumentChunkCandidate> chunks = chunkingService.chunk(101L, 7L, "Redis cache evidence.");

        assertThat(chunks).hasSize(1);
        DocumentChunkCandidate chunk = chunks.get(0);
        assertThat(chunk.documentId()).isEqualTo(101L);
        assertThat(chunk.userId()).isEqualTo(7L);
        assertThat(chunk.chunkIndex()).isZero();
        assertThat(chunk.content()).isEqualTo("Redis cache evidence.");
        assertThat(chunk.startOffset()).isZero();
        assertThat(chunk.endOffset()).isEqualTo("Redis cache evidence.".length());
        assertThat(chunk.tokenCount()).isEqualTo(chunk.content().length());
        assertThat(chunk.contentHash()).hasSize(64);
    }

    @Test
    void shouldCreateMultipleChunksForLongText() {
        ChunkingOptions options = new ChunkingOptions(10, 2);

        List<DocumentChunkCandidate> chunks = chunkingService.chunk(101L, 7L, "0123456789ABCDEFGHIJ", options);

        assertThat(chunks).hasSize(3);
        assertThat(chunks).extracting(DocumentChunkCandidate::content)
                .containsExactly("0123456789", "89ABCDEFGH", "GHIJ");
        assertThat(chunks).extracting(DocumentChunkCandidate::chunkIndex)
                .containsExactly(0, 1, 2);
    }

    @Test
    void shouldApplyOverlapWhenSplittingLongParagraph() {
        ChunkingOptions options = new ChunkingOptions(10, 3);

        List<DocumentChunkCandidate> chunks = chunkingService.chunk(101L, 7L, "0123456789ABCDEFGHIJ", options);

        assertThat(chunks).hasSize(3);
        assertThat(chunks.get(0).content()).isEqualTo("0123456789");
        assertThat(chunks.get(1).content()).isEqualTo("789ABCDEFG");
        assertThat(chunks.get(1).startOffset()).isEqualTo(7);
    }

    @Test
    void shouldGenerateContinuousChunkIndexes() {
        ChunkingOptions options = new ChunkingOptions(8, 2);

        List<DocumentChunkCandidate> chunks = chunkingService.chunk(101L, 7L, "0123456789ABCDEFGHIJKLMN", options);

        assertThat(chunks).extracting(DocumentChunkCandidate::chunkIndex)
                .containsExactly(0, 1, 2, 3);
    }

    @Test
    void shouldGenerateStableHashForSameContent() {
        List<DocumentChunkCandidate> first = chunkingService.chunk(101L, 7L, "same content");
        List<DocumentChunkCandidate> second = chunkingService.chunk(101L, 7L, "same content");

        assertThat(first.get(0).contentHash()).isEqualTo(second.get(0).contentHash());
    }

    @Test
    void shouldGenerateDifferentHashForDifferentContent() {
        List<DocumentChunkCandidate> first = chunkingService.chunk(101L, 7L, "first content");
        List<DocumentChunkCandidate> second = chunkingService.chunk(101L, 7L, "second content");

        assertThat(first.get(0).contentHash()).isNotEqualTo(second.get(0).contentHash());
    }

    @Test
    void shouldReturnEmptyChunksForBlankText() {
        assertThat(chunkingService.chunk(101L, 7L, null)).isEmpty();
        assertThat(chunkingService.chunk(101L, 7L, "   \n\n\t  ")).isEmpty();
    }

    @Test
    void shouldPreferParagraphBoundaries() {
        String text = "first paragraph\n\nsecond paragraph";

        List<DocumentChunkCandidate> chunks = chunkingService.chunk(101L, 7L, text, new ChunkingOptions(100, 10));

        assertThat(chunks).hasSize(2);
        assertThat(chunks).extracting(DocumentChunkCandidate::content)
                .containsExactly("first paragraph", "second paragraph");
        assertThat(chunks.get(0).startOffset()).isZero();
        assertThat(chunks.get(1).startOffset()).isEqualTo(text.indexOf("second"));
    }

    @Test
    void shouldSplitLongParagraphByChunkSize() {
        List<DocumentChunkCandidate> chunks = chunkingService.chunk(101L, 7L, "abcdefghijklmnop", new ChunkingOptions(6, 1));

        assertThat(chunks).extracting(DocumentChunkCandidate::content)
                .containsExactly("abcdef", "fghijk", "klmnop");
    }

    @Test
    void shouldKeepOffsetsReasonableAfterTrimming() {
        String text = "  first paragraph  \n\n  second paragraph  ";

        List<DocumentChunkCandidate> chunks = chunkingService.chunk(101L, 7L, text, new ChunkingOptions(100, 10));

        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        assertThat(chunks).hasSize(2);
        for (DocumentChunkCandidate chunk : chunks) {
            assertThat(normalized.substring(chunk.startOffset(), chunk.endOffset()))
                    .isEqualTo(chunk.content());
        }
    }

    @Test
    void shouldRejectInvalidOptions() {
        assertThatThrownBy(() -> new ChunkingOptions(10, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("overlap");
    }
}
