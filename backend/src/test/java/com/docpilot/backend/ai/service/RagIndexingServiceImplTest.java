package com.docpilot.backend.ai.service;

import com.docpilot.backend.ai.entity.DocumentChunkEntity;
import com.docpilot.backend.ai.rag.ChunkingService;
import com.docpilot.backend.ai.rag.ChunkingOptions;
import com.docpilot.backend.ai.rag.DocumentChunkCandidate;
import com.docpilot.backend.ai.rag.DocumentChunkIndexStatus;
import com.docpilot.backend.ai.rag.EmbeddingProvider;
import com.docpilot.backend.ai.rag.EmbeddingRequest;
import com.docpilot.backend.ai.rag.EmbeddingResult;
import com.docpilot.backend.ai.rag.EmbeddingVector;
import com.docpilot.backend.ai.rag.RagEmbeddingProperties;
import com.docpilot.backend.ai.rag.RagIndexingRequest;
import com.docpilot.backend.ai.rag.RagIndexingResult;
import com.docpilot.backend.ai.rag.RagIndexingStatus;
import com.docpilot.backend.ai.rag.RagVectorStoreProperties;
import com.docpilot.backend.ai.rag.vector.VectorPoint;
import com.docpilot.backend.ai.rag.vector.VectorSearchRequest;
import com.docpilot.backend.ai.rag.vector.VectorSearchResult;
import com.docpilot.backend.ai.rag.vector.VectorStoreClient;
import com.docpilot.backend.ai.rag.vector.inmemory.InMemoryVectorStoreClient;
import com.docpilot.backend.ai.service.impl.RagIndexingServiceImpl;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RagIndexingServiceImplTest {

    @Test
    void shouldIndexChunksWithEmbeddingAndVectorMetadataInOrder() {
        List<String> events = new ArrayList<>();
        FakeChunkingService chunkingService = new FakeChunkingService(events, List.of(
                structuredCandidate(0, "alpha chunk", "hash-a", 0, 11, 11,
                        "Alpha Section", 1, 0, "section", "none"),
                candidate(1, "beta chunk", "hash-b", 12, 22, 10)
        ));
        FakeDocumentChunkService chunkService = new FakeDocumentChunkService(events);
        FakeEmbeddingProvider embeddingProvider = new FakeEmbeddingProvider(events, List.of(
                embedding("model-a", 0.1D, 0.2D),
                embedding("model-a", 0.3D, 0.4D)
        ));
        FakeVectorStoreClient vectorClient = new FakeVectorStoreClient(events);

        RagIndexingResult result = service(
                chunkingService,
                chunkService,
                embeddingProvider,
                vectorClient,
                new RagVectorStoreProperties()
        ).index(new RagIndexingRequest(61L, 7L, "alpha\nbeta", null, "model-a"));

        assertThat(result.status()).isEqualTo(RagIndexingStatus.SUCCESS);
        assertThat(result.indexVersion()).isEqualTo(1);
        assertThat(result.chunkCount()).isEqualTo(2);
        assertThat(result.vectorCount()).isEqualTo(2);
        assertThat(result.message()).contains("replace semantics");
        assertThat(events).containsExactly("chunk", "embedBatch", "ensureReady", "delete", "replace", "upsert", "markIndexed");
        assertThat(embeddingProvider.requests).extracting(EmbeddingRequest::input)
                .containsExactly("alpha chunk", "beta chunk");
        assertThat(embeddingProvider.requests).extracting(EmbeddingRequest::model)
                .containsExactly("model-a", "model-a");
        assertThat(embeddingProvider.requests.get(0).metadata())
                .containsEntry("sectionTitle", "Alpha Section")
                .containsEntry("sectionOrdinal", "1")
                .containsEntry("sectionPath", "Alpha Section / Details")
                .containsEntry("sourceBlockOrdinal", "0")
                .containsEntry("structureType", "section")
                .containsEntry("qualityFlags", "none");
        assertThat(vectorClient.points).hasSize(2);

        VectorPoint firstPoint = vectorClient.points.get(0);
        assertThat(firstPoint.id()).isEqualTo(chunkService.savedChunks.get(0).getVectorId());
        assertThat(firstPoint.payload())
                .containsEntry("userId", 7L)
                .containsEntry("documentId", 61L)
                .containsEntry("indexVersion", 1)
                .containsEntry("chunkId", 100L)
                .containsEntry("chunkIndex", 0)
                .containsEntry("contentHash", "hash-a")
                .containsEntry("startOffset", 0)
                .containsEntry("endOffset", 11)
                .containsEntry("tokenCount", 11)
                .containsEntry("embeddingModel", "model-a")
                .containsEntry("sectionTitle", "Alpha Section")
                .containsEntry("sectionOrdinal", "1")
                .containsEntry("sectionPath", "Alpha Section / Details")
                .containsEntry("sourceBlockOrdinal", "0")
                .containsEntry("structureType", "section")
                .containsEntry("qualityFlags", "none")
                .containsEntry("content", "alpha chunk");
        assertThat(chunkService.indexedChunks).hasSize(2);
        assertThat(chunkService.indexedChunks).extracting(DocumentChunkEntity::getIndexStatus)
                .containsExactly(DocumentChunkIndexStatus.INDEXED, DocumentChunkIndexStatus.INDEXED);
    }

    @Test
    void shouldSkipBlankTextWithoutWritingDbOrVectorStore() {
        List<String> events = new ArrayList<>();
        RagIndexingResult result = service(
                new FakeChunkingService(events, List.of()),
                new FakeDocumentChunkService(events),
                new FakeEmbeddingProvider(events, List.of()),
                new FakeVectorStoreClient(events),
                new RagVectorStoreProperties()
        ).index(new RagIndexingRequest(61L, 7L, "   ", 2, ""));

        assertThat(result.status()).isEqualTo(RagIndexingStatus.SKIPPED_EMPTY_TEXT);
        assertThat(result.indexVersion()).isEqualTo(2);
        assertThat(events).isEmpty();
    }

    @Test
    void shouldNotDeleteOldIndexWhenEmbeddingProviderFails() {
        List<String> events = new ArrayList<>();
        FakeEmbeddingProvider embeddingProvider = new FakeEmbeddingProvider(events, List.of());
        embeddingProvider.failure = new IllegalStateException("provider unavailable");
        FakeVectorStoreClient vectorClient = new FakeVectorStoreClient(events);
        FakeDocumentChunkService chunkService = new FakeDocumentChunkService(events);

        RagIndexingResult result = service(
                new FakeChunkingService(events, List.of(candidate(0, "alpha", "hash-a", 0, 5, 5))),
                chunkService,
                embeddingProvider,
                vectorClient,
                new RagVectorStoreProperties()
        ).index(new RagIndexingRequest(61L, 7L, "alpha", 1, ""));

        assertThat(result.status()).isEqualTo(RagIndexingStatus.FAILED);
        assertThat(result.message()).contains("embedding failed").contains("replace semantics");
        assertThat(events).containsExactly("chunk", "embedBatch");
        assertThat(vectorClient.deleteRequests).isEmpty();
        assertThat(chunkService.replaceCalls).isZero();
    }

    @Test
    void shouldFailBeforeReplaceWhenEmbeddingCountDoesNotMatchChunkCount() {
        List<String> events = new ArrayList<>();
        FakeVectorStoreClient vectorClient = new FakeVectorStoreClient(events);
        FakeDocumentChunkService chunkService = new FakeDocumentChunkService(events);

        RagIndexingResult result = service(
                new FakeChunkingService(events, List.of(
                        candidate(0, "alpha", "hash-a", 0, 5, 5),
                        candidate(1, "beta", "hash-b", 6, 10, 4)
                )),
                chunkService,
                new FakeEmbeddingProvider(events, List.of(embedding("", 0.1D, 0.2D))),
                vectorClient,
                new RagVectorStoreProperties()
        ).index(new RagIndexingRequest(61L, 7L, "alpha beta", 1, ""));

        assertThat(result.status()).isEqualTo(RagIndexingStatus.FAILED);
        assertThat(result.message()).contains("embedding count 1 does not match chunk count 2");
        assertThat(events).containsExactly("chunk", "embedBatch");
        assertThat(vectorClient.deleteRequests).isEmpty();
        assertThat(chunkService.replaceCalls).isZero();
    }

    @Test
    void shouldFailBeforeReplaceWhenEmbeddingDimensionsAreInconsistent() {
        List<String> events = new ArrayList<>();
        FakeVectorStoreClient vectorClient = new FakeVectorStoreClient(events);

        RagIndexingResult result = service(
                new FakeChunkingService(events, List.of(
                        candidate(0, "alpha", "hash-a", 0, 5, 5),
                        candidate(1, "beta", "hash-b", 6, 10, 4)
                )),
                new FakeDocumentChunkService(events),
                new FakeEmbeddingProvider(events, List.of(
                        embedding("", 0.1D, 0.2D),
                        embedding("", 0.1D, 0.2D, 0.3D)
                )),
                vectorClient,
                new RagVectorStoreProperties()
        ).index(new RagIndexingRequest(61L, 7L, "alpha beta", 1, ""));

        assertThat(result.status()).isEqualTo(RagIndexingStatus.FAILED);
        assertThat(result.message()).contains("does not match 2");
        assertThat(events).containsExactly("chunk", "embedBatch");
        assertThat(vectorClient.deleteRequests).isEmpty();
    }

    @Test
    void shouldFailBeforeReplaceWhenQdrantDimensionDoesNotMatchEmbeddingDimension() {
        List<String> events = new ArrayList<>();
        RagVectorStoreProperties properties = new RagVectorStoreProperties();
        properties.setProvider(RagVectorStoreProperties.PROVIDER_QDRANT);
        properties.getQdrant().setDimension(3);
        FakeVectorStoreClient vectorClient = new FakeVectorStoreClient(events);

        RagIndexingResult result = service(
                new FakeChunkingService(events, List.of(candidate(0, "alpha", "hash-a", 0, 5, 5))),
                new FakeDocumentChunkService(events),
                new FakeEmbeddingProvider(events, List.of(embedding("", 0.1D, 0.2D))),
                vectorClient,
                properties
        ).index(new RagIndexingRequest(61L, 7L, "alpha", 1, ""));

        assertThat(result.status()).isEqualTo(RagIndexingStatus.FAILED);
        assertThat(result.message()).contains("does not match Qdrant dimension 3");
        assertThat(events).containsExactly("chunk", "embedBatch");
        assertThat(vectorClient.deleteRequests).isEmpty();
    }

    @Test
    void shouldMarkFailedAndCleanupVectorsWhenUpsertFails() {
        List<String> events = new ArrayList<>();
        FakeDocumentChunkService chunkService = new FakeDocumentChunkService(events);
        FakeVectorStoreClient vectorClient = new FakeVectorStoreClient(events);
        vectorClient.upsertFailure = new IllegalStateException("upsert down");

        RagIndexingResult result = service(
                new FakeChunkingService(events, List.of(
                        candidate(0, "alpha", "hash-a", 0, 5, 5),
                        candidate(1, "beta", "hash-b", 6, 10, 4)
                )),
                chunkService,
                new FakeEmbeddingProvider(events, List.of(
                        embedding("model-a", 0.1D, 0.2D),
                        embedding("model-a", 0.3D, 0.4D)
                )),
                vectorClient,
                new RagVectorStoreProperties()
        ).index(new RagIndexingRequest(61L, 7L, "alpha beta", 4, "model-a"));

        assertThat(result.status()).isEqualTo(RagIndexingStatus.FAILED);
        assertThat(result.message()).contains("upsert down").contains("replace semantics");
        assertThat(events).containsExactly(
                "chunk",
                "embedBatch",
                "ensureReady",
                "delete",
                "replace",
                "upsert",
                "markFailed",
                "delete"
        );
        assertThat(vectorClient.deleteRequests).containsExactly(
                "7:61:4",
                "7:61:4"
        );
        assertThat(chunkService.failedChunks).hasSize(2);
        assertThat(chunkService.failedChunks).extracting(DocumentChunkEntity::getIndexStatus)
                .containsExactly(DocumentChunkIndexStatus.FAILED, DocumentChunkIndexStatus.FAILED);
    }

    @Test
    void shouldNotCleanupOldVectorsWhenEnsureReadyFailsBeforeReplace() {
        List<String> events = new ArrayList<>();
        FakeDocumentChunkService chunkService = new FakeDocumentChunkService(events);
        FakeVectorStoreClient vectorClient = new FakeVectorStoreClient(events);
        vectorClient.ensureReadyFailure = new IllegalStateException("collection unavailable");

        RagIndexingResult result = service(
                new FakeChunkingService(events, List.of(candidate(0, "alpha", "hash-a", 0, 5, 5))),
                chunkService,
                new FakeEmbeddingProvider(events, List.of(embedding("model-a", 0.1D, 0.2D))),
                vectorClient,
                new RagVectorStoreProperties()
        ).index(new RagIndexingRequest(61L, 7L, "alpha", 4, "model-a"));

        assertThat(result.status()).isEqualTo(RagIndexingStatus.FAILED);
        assertThat(events).containsExactly("chunk", "embedBatch", "ensureReady");
        assertThat(vectorClient.deleteRequests).isEmpty();
        assertThat(chunkService.replaceCalls).isZero();
        assertThat(chunkService.failedChunks).isEmpty();
    }

    @Test
    void shouldExposeReplaceSemanticsForIndexRebuildAndRetry() {
        List<String> events = new ArrayList<>();
        RagIndexingService service = service(
                new FakeChunkingService(events, List.of(candidate(0, "alpha", "hash-a", 0, 5, 5))),
                new FakeDocumentChunkService(events),
                new FakeEmbeddingProvider(events, List.of(embedding("", 0.1D, 0.2D))),
                new FakeVectorStoreClient(events),
                new RagVectorStoreProperties()
        );

        assertThat(service.index(new RagIndexingRequest(61L, 7L, "alpha", 1, "")).message())
                .contains("replace semantics");
        assertThat(service.rebuild(new RagIndexingRequest(61L, 7L, "alpha", 1, "")).message())
                .contains("replace semantics");
        assertThat(service.retry(new RagIndexingRequest(61L, 7L, "alpha", 1, "")).message())
                .contains("replace semantics");
    }

    @Test
    void inMemoryEnsureReadyShouldBeNoop() {
        InMemoryVectorStoreClient client = new InMemoryVectorStoreClient();

        client.ensureReady();

        assertThat(client.size()).isZero();
    }

    private RagIndexingService service(ChunkingService chunkingService,
                                       DocumentChunkService chunkService,
                                       EmbeddingProvider embeddingProvider,
                                       VectorStoreClient vectorStoreClient,
                                       RagVectorStoreProperties vectorStoreProperties) {
        return new RagIndexingServiceImpl(
                chunkingService,
                chunkService,
                embeddingProvider,
                vectorStoreClient,
                new RagEmbeddingProperties(),
                vectorStoreProperties
        );
    }

    private static DocumentChunkCandidate candidate(int chunkIndex,
                                                    String content,
                                                    String contentHash,
                                                    int startOffset,
                                                    int endOffset,
                                                    int tokenCount) {
        return new DocumentChunkCandidate(61L, 7L, chunkIndex, content, contentHash, startOffset, endOffset, tokenCount);
    }

    private static DocumentChunkCandidate structuredCandidate(int chunkIndex,
                                                              String content,
                                                              String contentHash,
                                                              int startOffset,
                                                              int endOffset,
                                                              int tokenCount,
                                                              String sectionTitle,
                                                              int sectionOrdinal,
                                                              int sourceBlockOrdinal,
                                                              String structureType,
                                                              String qualityFlags) {
        return new DocumentChunkCandidate(61L, 7L, chunkIndex, content, contentHash, startOffset, endOffset, tokenCount,
                sectionTitle, sectionOrdinal, "Alpha Section / Details", sourceBlockOrdinal, structureType, qualityFlags);
    }

    private static EmbeddingResult embedding(String model, Double... values) {
        EmbeddingVector vector = new EmbeddingVector(List.of(values));
        return new EmbeddingResult(vector, "fake", model, vector.dimension(), Map.of());
    }

    private static final class FakeChunkingService implements ChunkingService {

        private final List<String> events;
        private final List<DocumentChunkCandidate> candidates;

        private FakeChunkingService(List<String> events, List<DocumentChunkCandidate> candidates) {
            this.events = events;
            this.candidates = candidates;
        }

        @Override
        public List<DocumentChunkCandidate> chunk(Long documentId, Long userId, String text) {
            events.add("chunk");
            return candidates;
        }

        @Override
        public List<DocumentChunkCandidate> chunk(Long documentId, Long userId, String text, ChunkingOptions options) {
            return chunk(documentId, userId, text);
        }
    }

    private static final class FakeEmbeddingProvider implements EmbeddingProvider {

        private final List<String> events;
        private final List<EmbeddingResult> results;
        private final List<EmbeddingRequest> requests = new ArrayList<>();
        private RuntimeException failure;

        private FakeEmbeddingProvider(List<String> events, List<EmbeddingResult> results) {
            this.events = events;
            this.results = results;
        }

        @Override
        public EmbeddingResult embed(EmbeddingRequest request) {
            throw new UnsupportedOperationException("embed is not used by this test fake");
        }

        @Override
        public List<EmbeddingResult> embedBatch(List<EmbeddingRequest> requests) {
            events.add("embedBatch");
            if (failure != null) {
                throw failure;
            }
            this.requests.addAll(requests);
            List<EmbeddingResult> resolved = new ArrayList<>(results.size());
            for (int i = 0; i < results.size(); i++) {
                EmbeddingResult result = results.get(i);
                Map<String, String> metadata = i < requests.size() ? requests.get(i).metadata() : result.metadata();
                resolved.add(new EmbeddingResult(result.vector(), result.provider(), result.model(),
                        result.dimension(), metadata));
            }
            return resolved;
        }
    }

    private static final class FakeVectorStoreClient implements VectorStoreClient {

        private final List<String> events;
        private final List<VectorPoint> points = new ArrayList<>();
        private final List<String> deleteRequests = new ArrayList<>();
        private RuntimeException ensureReadyFailure;
        private RuntimeException upsertFailure;

        private FakeVectorStoreClient(List<String> events) {
            this.events = events;
        }

        @Override
        public void ensureReady() {
            events.add("ensureReady");
            if (ensureReadyFailure != null) {
                throw ensureReadyFailure;
            }
        }

        @Override
        public void upsert(List<VectorPoint> points) {
            events.add("upsert");
            if (upsertFailure != null) {
                throw upsertFailure;
            }
            this.points.addAll(points);
        }

        @Override
        public VectorSearchResult search(VectorSearchRequest request) {
            throw new UnsupportedOperationException("search is not used by indexing tests");
        }

        @Override
        public void deleteByDocumentId(Long userId, Long documentId, Integer indexVersion) {
            events.add("delete");
            deleteRequests.add(userId + ":" + documentId + ":" + indexVersion);
        }
    }

    private static final class FakeDocumentChunkService implements DocumentChunkService {

        private final List<String> events;
        private final List<DocumentChunkEntity> savedChunks = new ArrayList<>();
        private final List<DocumentChunkEntity> indexedChunks = new ArrayList<>();
        private final List<DocumentChunkEntity> failedChunks = new ArrayList<>();
        private int replaceCalls;

        private FakeDocumentChunkService(List<String> events) {
            this.events = events;
        }

        @Override
        public List<DocumentChunkEntity> saveChunks(Long documentId,
                                                    Long userId,
                                                    List<DocumentChunkCandidate> chunks,
                                                    Integer indexVersion) {
            return toEntities(documentId, userId, chunks, indexVersion);
        }

        @Override
        public List<DocumentChunkEntity> listByDocumentId(Long documentId) {
            return savedChunks;
        }

        @Override
        public List<DocumentChunkEntity> listByDocumentIdAndVersion(Long documentId, Integer indexVersion) {
            return savedChunks.stream()
                    .filter(chunk -> documentId.equals(chunk.getDocumentId()))
                    .filter(chunk -> indexVersion.equals(chunk.getIndexVersion()))
                    .toList();
        }

        @Override
        public int deleteByDocumentIdAndVersion(Long documentId, Integer indexVersion) {
            return 0;
        }

        @Override
        public List<DocumentChunkEntity> replaceChunks(Long documentId, Long userId, String text, Integer indexVersion) {
            throw new UnsupportedOperationException("text replace is not used by indexing workflow");
        }

        @Override
        public List<DocumentChunkEntity> replaceChunks(Long documentId,
                                                       Long userId,
                                                       List<DocumentChunkCandidate> chunks,
                                                       Integer indexVersion) {
            events.add("replace");
            replaceCalls++;
            savedChunks.clear();
            savedChunks.addAll(toEntities(documentId, userId, chunks, indexVersion));
            return savedChunks;
        }

        @Override
        public void markIndexed(List<DocumentChunkEntity> chunks) {
            events.add("markIndexed");
            chunks.forEach(chunk -> chunk.setIndexStatus(DocumentChunkIndexStatus.INDEXED));
            indexedChunks.addAll(chunks);
        }

        @Override
        public void markFailed(List<DocumentChunkEntity> chunks) {
            events.add("markFailed");
            chunks.forEach(chunk -> chunk.setIndexStatus(DocumentChunkIndexStatus.FAILED));
            failedChunks.addAll(chunks);
        }

        private List<DocumentChunkEntity> toEntities(Long documentId,
                                                     Long userId,
                                                     List<DocumentChunkCandidate> chunks,
                                                     Integer indexVersion) {
            List<DocumentChunkEntity> entities = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                DocumentChunkCandidate candidate = chunks.get(i);
                DocumentChunkEntity entity = new DocumentChunkEntity();
                entity.setId(100L + i);
                entity.setDocumentId(documentId);
                entity.setUserId(userId);
                entity.setChunkIndex(candidate.chunkIndex());
                entity.setContent(candidate.content());
                entity.setContentHash(candidate.contentHash());
                entity.setStartOffset(candidate.startOffset());
                entity.setEndOffset(candidate.endOffset());
                entity.setTokenCount(candidate.tokenCount());
                entity.setIndexVersion(indexVersion);
                entity.setIndexStatus(DocumentChunkIndexStatus.PENDING);
                entity.setCreateTime(LocalDateTime.now());
                entity.setUpdateTime(LocalDateTime.now());
                entities.add(entity);
            }
            return entities;
        }
    }
}
