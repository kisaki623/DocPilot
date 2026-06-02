package com.docpilot.backend.ai.rag.vector.qdrant;

import com.docpilot.backend.ai.rag.EmbeddingVector;
import com.docpilot.backend.ai.rag.RagVectorStoreProperties;
import com.docpilot.backend.ai.rag.vector.VectorPoint;
import com.docpilot.backend.ai.rag.vector.VectorSearchRequest;
import com.docpilot.backend.ai.rag.vector.VectorSearchResult;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("smoke")
@EnabledIfEnvironmentVariable(named = "DOCPILOT_QDRANT_REMOTE_SMOKE_ENABLED", matches = "true")
class QdrantVectorStoreClientRemoteSmokeTest {

    private static final String COLLECTION = "docpilot_t003b_smoke";
    private static final String DEFAULT_ENDPOINT = "http://127.0.0.1:6333";

    @Test
    void shouldVerifyRemoteQdrantThroughVectorStoreClient() throws Exception {
        String endpoint = smokeEndpoint();
        RagVectorStoreProperties.Qdrant properties = qdrantProperties(endpoint);
        QdrantVectorStoreClient client = new QdrantVectorStoreClient(properties);

        try {
            client.deleteCollectionIfExists();
            client.ensureCollection();
            client.upsert(List.of(
                    point("11111111-1111-4111-8111-111111111111", 1001L, 2001L, 1, 0,
                            "remote smoke primary chunk", vector(1.0D, 0.0D, 0.0D, 0.0D)),
                    point("22222222-2222-4222-8222-222222222222", 1001L, 2002L, 1, 0,
                            "remote smoke isolated chunk", vector(0.0D, 1.0D, 0.0D, 0.0D))
            ));

            VectorSearchResult filteredResult = client.search(new VectorSearchRequest(
                    1001L,
                    2001L,
                    1,
                    vector(1.0D, 0.0D, 0.0D, 0.0D),
                    5
            ));

            assertThat(filteredResult.hits()).hasSize(1);
            assertThat(filteredResult.hits().get(0).documentId()).isEqualTo(2001L);
            assertThat(filteredResult.hits().get(0).payload())
                    .containsEntry("userId", 1001)
                    .containsEntry("documentId", 2001)
                    .containsEntry("indexVersion", 1)
                    .containsEntry("chunkIndex", 0);

            client.deleteByDocumentId(1001L, 2001L, 1);

            VectorSearchResult deletedDocumentResult = client.search(new VectorSearchRequest(
                    1001L,
                    2001L,
                    1,
                    vector(1.0D, 0.0D, 0.0D, 0.0D),
                    5
            ));
            assertThat(deletedDocumentResult.hits()).isEmpty();

            VectorSearchResult isolatedDocumentResult = client.search(new VectorSearchRequest(
                    1001L,
                    2002L,
                    1,
                    vector(0.0D, 1.0D, 0.0D, 0.0D),
                    5
            ));
            assertThat(isolatedDocumentResult.hits()).hasSize(1);
            assertThat(isolatedDocumentResult.hits().get(0).documentId()).isEqualTo(2002L);
        } finally {
            client.deleteCollectionIfExists();
        }

        assertThat(collectionStatusCode(endpoint)).isEqualTo(404);
    }

    private RagVectorStoreProperties.Qdrant qdrantProperties(String endpoint) {
        RagVectorStoreProperties.Qdrant properties = new RagVectorStoreProperties.Qdrant();
        properties.setEndpoint(endpoint);
        properties.setCollection(COLLECTION);
        properties.setDimension(4);
        properties.setDistance("Cosine");
        properties.setConnectTimeoutMs(1000);
        properties.setRequestTimeoutMs(5000);
        return properties;
    }

    private VectorPoint point(String id,
                              Long userId,
                              Long documentId,
                              Integer indexVersion,
                              Integer chunkIndex,
                              String content,
                              EmbeddingVector vector) {
        return new VectorPoint(id, userId, documentId, indexVersion, chunkIndex, content,
                "hash-" + documentId + "-" + chunkIndex, vector, Map.of("source", "t003b-smoke"));
    }

    private EmbeddingVector vector(Double... values) {
        return new EmbeddingVector(List.of(values));
    }

    private String smokeEndpoint() {
        String configuredEndpoint = System.getenv("DOCPILOT_QDRANT_REMOTE_SMOKE_ENDPOINT");
        if (configuredEndpoint == null || configuredEndpoint.isBlank()) {
            return DEFAULT_ENDPOINT;
        }
        return configuredEndpoint.trim();
    }

    private int collectionStatusCode(String endpoint) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(stripTrailingSlash(endpoint)
                        + "/collections/" + COLLECTION))
                .GET()
                .build();
        return HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.discarding())
                .statusCode();
    }

    private String stripTrailingSlash(String endpoint) {
        String normalized = endpoint.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
