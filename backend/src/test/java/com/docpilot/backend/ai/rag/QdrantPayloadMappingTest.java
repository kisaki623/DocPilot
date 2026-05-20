package com.docpilot.backend.ai.rag;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QdrantPayloadMappingTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldBuildUpsertPayloadWithVectorAndMetadata() throws Exception {
        DocumentChunk chunk = new DocumentChunk(61L, 2, "sanitized chunk text",
                Map.of(
                        "contentHash", "content-hash-61-2",
                        "charStart", "120",
                        "charEnd", "180",
                        "chunkVersion", "fake-rag-v1",
                        "source", "unit-test"
                ));
        QdrantPointPayload point = QdrantPointPayload.fromChunk("user-1", "v1", chunk,
                vector(0.1D, 0.2D, 0.3D));

        String json = new QdrantUpsertRequestBuilder().buildJson(List.of(point));
        Map<String, Object> request = readMap(json);
        Map<String, Object> firstPoint = firstPoint(request);
        Map<String, Object> payload = castMap(firstPoint.get("payload"));
        Map<String, Object> citation = castMap(payload.get("citation"));

        assertThat(firstPoint.get("id")).isEqualTo("61:v1:2:content-hash-61-2");
        assertThat(firstPoint.get("vector")).isEqualTo(List.of(0.1D, 0.2D, 0.3D));
        assertThat(payload)
                .containsEntry("userId", "user-1")
                .containsEntry("documentId", 61)
                .containsEntry("documentVersion", "v1")
                .containsEntry("chunkIndex", 2)
                .containsEntry("contentHash", "content-hash-61-2");
        assertThat(citation)
                .containsEntry("charStart", "120")
                .containsEntry("charEnd", "180")
                .containsEntry("chunkVersion", "fake-rag-v1")
                .containsEntry("source", "unit-test");
    }

    @Test
    void shouldBuildSearchPayloadWithUserAndDocumentFilter() throws Exception {
        String json = new QdrantSearchRequestBuilder().buildJson(RagSearchScope.of("user-1", 61L),
                vector(0.4D, 0.5D), 3);

        Map<String, Object> request = readMap(json);
        Map<String, Object> filter = castMap(request.get("filter"));
        List<Map<String, Object>> must = castList(filter.get("must"));

        assertThat(request.get("vector")).isEqualTo(List.of(0.4D, 0.5D));
        assertThat(request.get("limit")).isEqualTo(3);
        assertThat(request.get("with_payload")).isEqualTo(true);
        assertThat(must).anySatisfy(condition -> {
            assertThat(condition.get("key")).isEqualTo("userId");
            assertThat(castMap(condition.get("match"))).containsEntry("value", "user-1");
        });
        assertThat(must).anySatisfy(condition -> {
            assertThat(condition.get("key")).isEqualTo("documentId");
            assertThat(castMap(condition.get("match"))).containsEntry("value", 61);
        });
    }

    @Test
    void shouldRejectSearchPayloadWithoutScope() {
        QdrantSearchRequestBuilder builder = new QdrantSearchRequestBuilder();

        assertThatThrownBy(() -> builder.buildJson((RagSearchScope) null, vector(0.4D, 0.5D), 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("scope");
        assertThatThrownBy(() -> builder.buildJson(" ", 61L, vector(0.4D, 0.5D), 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userId");
        assertThatThrownBy(() -> builder.buildJson("user-1", null, vector(0.4D, 0.5D), 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("documentId");
    }

    @Test
    void shouldParseSearchResponseScoreAndPayloadMetadata() {
        String responseJson = """
                {
                  "result": [
                    {
                      "id": "61:v1:0:hash-0",
                      "score": 0.91,
                      "payload": {
                        "userId": "user-1",
                        "documentId": 61,
                        "documentVersion": "v1",
                        "chunkIndex": 0,
                        "text": "sanitized chunk text",
                        "metadata": {
                          "charStart": "0",
                          "charEnd": "42",
                          "contentHash": "hash-0"
                        }
                      }
                    }
                  ]
                }
                """;

        List<QdrantRetrievedPoint> points = new QdrantSearchResponseParser().parsePoints(responseJson);
        VectorSearchResult result = points.get(0).toVectorSearchResult();

        assertThat(points).hasSize(1);
        assertThat(points.get(0).id()).isEqualTo("61:v1:0:hash-0");
        assertThat(points.get(0).score()).isEqualTo(0.91D);
        assertThat(result.chunk().documentId()).isEqualTo(61L);
        assertThat(result.chunk().chunkIndex()).isZero();
        assertThat(result.chunk().metadata())
                .containsEntry("charStart", "0")
                .containsEntry("charEnd", "42")
                .containsEntry("contentHash", "hash-0");
    }

    @Test
    void shouldRejectMalformedSearchResponse() {
        QdrantSearchResponseParser parser = new QdrantSearchResponseParser();

        assertThatThrownBy(() -> parser.parsePoints("{not-json"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Failed to parse Qdrant search response");
    }

    private EmbeddingVector vector(Double... values) {
        return new EmbeddingVector(List.of(values));
    }

    private Map<String, Object> readMap(String json) throws Exception {
        return objectMapper.readValue(json, new TypeReference<>() {
        });
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> firstPoint(Map<String, Object> request) {
        List<Map<String, Object>> points = (List<Map<String, Object>>) request.get("points");
        return points.get(0);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castList(Object value) {
        return (List<Map<String, Object>>) value;
    }
}
