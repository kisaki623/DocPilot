package com.docpilot.backend.ai.rag.vector.inmemory;

import com.docpilot.backend.ai.rag.EmbeddingVector;
import com.docpilot.backend.ai.rag.vector.VectorPoint;
import com.docpilot.backend.ai.rag.vector.VectorSearchRequest;
import com.docpilot.backend.ai.rag.vector.VectorSearchResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryVectorStoreClientTest {

    private final InMemoryVectorStoreClient client = new InMemoryVectorStoreClient();

    @Test
    void shouldUpsertAndSearchTopKDeterministically() {
        client.upsert(List.of(
                point("p0", 1L, 10L, 1, 0, "first", vector(1.0D, 0.0D)),
                point("p1", 1L, 10L, 1, 1, "second", vector(0.9D, 0.1D)),
                point("p2", 1L, 10L, 1, 2, "third", vector(0.0D, 1.0D))
        ));

        VectorSearchResult result = client.search(new VectorSearchRequest(1L, 10L, 1, vector(1.0D, 0.0D), 2));

        assertThat(result.provider()).isEqualTo("in_memory");
        assertThat(result.hits()).hasSize(2);
        assertThat(result.hits()).extracting("id").containsExactly("p0", "p1");
    }

    @Test
    void shouldFilterByUserDocumentAndVersion() {
        client.upsert(List.of(
                point("same-version", 1L, 10L, 1, 0, "match", vector(1.0D, 0.0D)),
                point("other-user", 2L, 10L, 1, 1, "skip", vector(1.0D, 0.0D)),
                point("other-doc", 1L, 11L, 1, 2, "skip", vector(1.0D, 0.0D)),
                point("other-version", 1L, 10L, 2, 3, "skip", vector(1.0D, 0.0D))
        ));

        VectorSearchResult result = client.search(new VectorSearchRequest(1L, 10L, 1, vector(1.0D, 0.0D), 10));

        assertThat(result.hits()).extracting("id").containsExactly("same-version");
    }

    @Test
    void shouldSearchAllVersionsWhenVersionIsNull() {
        client.upsert(List.of(
                point("v1", 1L, 10L, 1, 0, "first", vector(1.0D, 0.0D)),
                point("v2", 1L, 10L, 2, 1, "second", vector(1.0D, 0.0D))
        ));

        VectorSearchResult result = client.search(new VectorSearchRequest(1L, 10L, null, vector(1.0D, 0.0D), 10));

        assertThat(result.hits()).extracting("id").containsExactly("v1", "v2");
    }

    @Test
    void shouldReplaceExistingPointOnUpsert() {
        client.upsert(List.of(point("same-id", 1L, 10L, 1, 0, "old", vector(1.0D, 0.0D))));
        client.upsert(List.of(point("same-id", 1L, 10L, 1, 0, "new", vector(1.0D, 0.0D))));

        VectorSearchResult result = client.search(new VectorSearchRequest(1L, 10L, 1, vector(1.0D, 0.0D), 10));

        assertThat(client.size()).isEqualTo(1);
        assertThat(result.hits()).singleElement().extracting("content").isEqualTo("new");
    }

    @Test
    void shouldDeleteByDocumentIdAndOptionalVersion() {
        client.upsert(List.of(
                point("v1", 1L, 10L, 1, 0, "first", vector(1.0D, 0.0D)),
                point("v2", 1L, 10L, 2, 1, "second", vector(1.0D, 0.0D)),
                point("other-user", 2L, 10L, 1, 2, "third", vector(1.0D, 0.0D))
        ));

        client.deleteByDocumentId(1L, 10L, 1);

        assertThat(client.search(new VectorSearchRequest(1L, 10L, null, vector(1.0D, 0.0D), 10)).hits())
                .extracting("id")
                .containsExactly("v2");
        assertThat(client.search(new VectorSearchRequest(2L, 10L, null, vector(1.0D, 0.0D), 10)).hits())
                .extracting("id")
                .containsExactly("other-user");

        client.deleteByDocumentId(1L, 10L, null);

        assertThat(client.search(new VectorSearchRequest(1L, 10L, null, vector(1.0D, 0.0D), 10)).hits())
                .isEmpty();
    }

    @Test
    void shouldRejectDimensionMismatch() {
        client.upsert(List.of(point("p0", 1L, 10L, 1, 0, "first", vector(1.0D, 0.0D))));

        assertThatThrownBy(() -> client.search(new VectorSearchRequest(1L, 10L, 1, vector(1.0D), 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("query vector dimension must match indexed vector dimension");
    }

    private VectorPoint point(String id,
                              Long userId,
                              Long documentId,
                              Integer indexVersion,
                              Integer chunkIndex,
                              String content,
                              EmbeddingVector vector) {
        return new VectorPoint(id, userId, documentId, indexVersion, chunkIndex, content,
                "hash-" + id, vector, Map.of());
    }

    private EmbeddingVector vector(Double... values) {
        return new EmbeddingVector(List.of(values));
    }
}
