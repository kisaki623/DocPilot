package com.docpilot.backend.ai.service;

import com.docpilot.backend.ai.entity.DocumentChunkEntity;
import com.docpilot.backend.ai.rag.ChunkingOptions;
import com.docpilot.backend.ai.rag.ChunkingService;
import com.docpilot.backend.ai.rag.DocumentChunkCandidate;
import com.docpilot.backend.ai.rag.DocumentChunkIndexStatus;
import com.docpilot.backend.ai.rag.EmbeddingProvider;
import com.docpilot.backend.ai.rag.EmbeddingRequest;
import com.docpilot.backend.ai.rag.EmbeddingResult;
import com.docpilot.backend.ai.rag.EmbeddingVector;
import com.docpilot.backend.ai.rag.MockEmbeddingProvider;
import com.docpilot.backend.ai.rag.RagEmbeddingProperties;
import com.docpilot.backend.ai.rag.RagIndexingRequest;
import com.docpilot.backend.ai.rag.RagQaProperties;
import com.docpilot.backend.ai.rag.RagRetrievalQuery;
import com.docpilot.backend.ai.rag.RagRetrievalResult;
import com.docpilot.backend.ai.rag.RagVectorStoreProperties;
import com.docpilot.backend.ai.rag.vector.VectorSearchHit;
import com.docpilot.backend.ai.rag.vector.VectorSearchRequest;
import com.docpilot.backend.ai.rag.vector.VectorSearchResult;
import com.docpilot.backend.ai.rag.vector.VectorStoreClient;
import com.docpilot.backend.ai.rag.vector.inmemory.InMemoryVectorStoreClient;
import com.docpilot.backend.ai.service.impl.RagDocumentRetrievalServiceImpl;
import com.docpilot.backend.ai.service.impl.RagIndexingServiceImpl;
import com.docpilot.backend.common.error.ErrorCode;
import com.docpilot.backend.common.exception.BusinessException;
import com.docpilot.backend.document.entity.Document;
import com.docpilot.backend.document.mapper.DocumentMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagDocumentRetrievalServiceImplTest {

    @Mock
    private DocumentMapper documentMapper;

    @Mock
    private EmbeddingProvider embeddingProvider;

    @Mock
    private VectorStoreClient vectorStoreClient;

    @Test
    void shouldRetrieveWithStrictMetadataFilterAndDefaultVersion() {
        when(documentMapper.selectById(101L)).thenReturn(document(101L, 7L));
        when(embeddingProvider.embed(any())).thenReturn(embedding("mock-model"));
        when(vectorStoreClient.search(any())).thenReturn(new VectorSearchResult(List.of(hit()), "in_memory", ""));
        RagQaProperties qaProperties = new RagQaProperties();
        qaProperties.setTopK(3);
        RagDocumentRetrievalServiceImpl service = service(qaProperties);

        RagRetrievalResult result = service.retrieve(new RagRetrievalQuery(
                7L,
                101L,
                "cache policy",
                50,
                null,
                ""
        ));

        ArgumentCaptor<VectorSearchRequest> searchCaptor = ArgumentCaptor.forClass(VectorSearchRequest.class);
        org.mockito.Mockito.verify(vectorStoreClient).search(searchCaptor.capture());
        VectorSearchRequest searchRequest = searchCaptor.getValue();
        assertThat(searchRequest.userId()).isEqualTo(7L);
        assertThat(searchRequest.documentId()).isEqualTo(101L);
        assertThat(searchRequest.indexVersion()).isEqualTo(1);
        assertThat(searchRequest.topK()).isEqualTo(10);
        assertThat(result.noEvidence()).isFalse();
        assertThat(result.hits()).hasSize(1);
        assertThat(result.citations()).hasSize(1);
        assertThat(result.citations().get(0).index()).isEqualTo(1);
        assertThat(result.citations().get(0).chunkId()).isEqualTo(501L);
    }

    @Test
    void shouldUseConfiguredDefaultTopKAndReturnNoEvidence() {
        when(documentMapper.selectById(101L)).thenReturn(document(101L, 7L));
        when(embeddingProvider.embed(any())).thenReturn(embedding(""));
        when(vectorStoreClient.search(any())).thenReturn(new VectorSearchResult(List.of(), "in_memory", ""));
        RagQaProperties qaProperties = new RagQaProperties();
        qaProperties.setTopK(4);
        RagDocumentRetrievalServiceImpl service = service(qaProperties);

        RagRetrievalResult result = service.retrieve(new RagRetrievalQuery(
                7L,
                101L,
                "missing",
                null,
                2,
                ""
        ));

        ArgumentCaptor<VectorSearchRequest> searchCaptor = ArgumentCaptor.forClass(VectorSearchRequest.class);
        org.mockito.Mockito.verify(vectorStoreClient).search(searchCaptor.capture());
        assertThat(searchCaptor.getValue().topK()).isEqualTo(4);
        assertThat(searchCaptor.getValue().indexVersion()).isEqualTo(2);
        assertThat(result.noEvidence()).isTrue();
        assertThat(result.citations()).isEmpty();
    }

    @Test
    void shouldRejectWrongOwnerBeforeEmbedding() {
        when(documentMapper.selectById(101L)).thenReturn(document(101L, 8L));
        RagDocumentRetrievalServiceImpl service = service(new RagQaProperties());

        BusinessException ex = assertThrows(BusinessException.class, () -> service.retrieve(new RagRetrievalQuery(
                7L,
                101L,
                "question",
                3,
                1,
                ""
        )));

        assertEquals(ErrorCode.DOCUMENT_FORBIDDEN, ex.getErrorCode());
        org.mockito.Mockito.verifyNoInteractions(embeddingProvider, vectorStoreClient);
    }

    @Test
    void shouldRejectVectorHitOutsideRequestedScope() {
        when(documentMapper.selectById(101L)).thenReturn(document(101L, 7L));
        when(embeddingProvider.embed(any())).thenReturn(embedding("mock-model"));
        when(vectorStoreClient.search(any())).thenReturn(new VectorSearchResult(
                List.of(hit(7L, 102L, 1)),
                "in_memory",
                ""
        ));
        RagDocumentRetrievalServiceImpl service = service(new RagQaProperties());

        BusinessException ex = assertThrows(BusinessException.class, () -> service.retrieve(new RagRetrievalQuery(
                7L,
                101L,
                "question",
                3,
                1,
                ""
        )));

        assertEquals(ErrorCode.DOCUMENT_FORBIDDEN, ex.getErrorCode());
    }

    @Test
    void shouldRejectBlankQuery() {
        RagDocumentRetrievalServiceImpl service = service(new RagQaProperties());

        BusinessException ex = assertThrows(BusinessException.class, () -> service.retrieve(new RagRetrievalQuery(
                7L,
                101L,
                " ",
                3,
                1,
                ""
        )));

        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
    }

    @Test
    void shouldRetrieveVectorsWrittenByIndexingWhenInMemoryClientIsShared() {
        InMemoryVectorStoreClient sharedClient = new InMemoryVectorStoreClient();
        MockEmbeddingProvider sharedEmbeddingProvider = new MockEmbeddingProvider(16, "mock-model");
        SimpleDocumentChunkService chunkService = new SimpleDocumentChunkService();
        RagIndexingServiceImpl indexingService = new RagIndexingServiceImpl(
                new SingleChunkingService(),
                chunkService,
                sharedEmbeddingProvider,
                sharedClient,
                new RagEmbeddingProperties(),
                new RagVectorStoreProperties()
        );
        when(documentMapper.selectById(101L)).thenReturn(document(101L, 7L));
        RagDocumentRetrievalServiceImpl retrievalService = new RagDocumentRetrievalServiceImpl(
                documentMapper,
                sharedEmbeddingProvider,
                sharedClient,
                new RagEmbeddingProperties(),
                new RagQaProperties()
        );

        indexingService.index(new RagIndexingRequest(
                101L,
                7L,
                "Redis cache stores QA answers.",
                1,
                "mock-model"
        ));
        RagRetrievalResult result = retrievalService.retrieve(new RagRetrievalQuery(
                7L,
                101L,
                "Redis cache",
                3,
                1,
                "mock-model"
        ));

        assertThat(sharedClient.size()).isEqualTo(1);
        assertThat(result.noEvidence()).isFalse();
        assertThat(result.hits()).hasSize(1);
        assertThat(result.hits().get(0).content()).contains("Redis cache");
        assertThat(result.citations().get(0).chunkId()).isEqualTo(900L);
    }

    private RagDocumentRetrievalServiceImpl service(RagQaProperties qaProperties) {
        return new RagDocumentRetrievalServiceImpl(
                documentMapper,
                embeddingProvider,
                vectorStoreClient,
                new RagEmbeddingProperties(),
                qaProperties
        );
    }

    private Document document(Long documentId, Long userId) {
        Document document = new Document();
        document.setId(documentId);
        document.setUserId(userId);
        return document;
    }

    private EmbeddingResult embedding(String model) {
        EmbeddingVector vector = new EmbeddingVector(List.of(0.1D, 0.2D, 0.3D));
        return new EmbeddingResult(vector, "mock", model, vector.dimension(), Map.of());
    }

    private VectorSearchHit hit() {
        return hit(7L, 101L, 1);
    }

    private VectorSearchHit hit(Long userId, Long documentId, Integer indexVersion) {
        return new VectorSearchHit(
                "vector-1",
                0.91D,
                userId,
                documentId,
                indexVersion,
                0,
                "cache policy evidence",
                "hash-a",
                Map.of(
                        "chunkId", 501L,
                        "startOffset", 10,
                        "endOffset", 31,
                        "tokenCount", 4,
                        "embeddingModel", "mock-model"
                )
        );
    }

    private static final class SingleChunkingService implements ChunkingService {

        @Override
        public List<DocumentChunkCandidate> chunk(Long documentId, Long userId, String text) {
            return List.of(new DocumentChunkCandidate(
                    documentId,
                    userId,
                    0,
                    text,
                    "hash-shared",
                    0,
                    text.length(),
                    5
            ));
        }

        @Override
        public List<DocumentChunkCandidate> chunk(Long documentId,
                                                  Long userId,
                                                  String text,
                                                  ChunkingOptions options) {
            return chunk(documentId, userId, text);
        }
    }

    private static final class SimpleDocumentChunkService implements DocumentChunkService {

        private final List<DocumentChunkEntity> chunks = new ArrayList<>();

        @Override
        public List<DocumentChunkEntity> saveChunks(Long documentId,
                                                    Long userId,
                                                    List<DocumentChunkCandidate> chunks,
                                                    Integer indexVersion) {
            return toEntities(documentId, userId, chunks, indexVersion);
        }

        @Override
        public List<DocumentChunkEntity> listByDocumentId(Long documentId) {
            return chunks.stream()
                    .filter(chunk -> documentId.equals(chunk.getDocumentId()))
                    .toList();
        }

        @Override
        public List<DocumentChunkEntity> listByDocumentIdAndVersion(Long documentId, Integer indexVersion) {
            return chunks.stream()
                    .filter(chunk -> documentId.equals(chunk.getDocumentId()))
                    .filter(chunk -> indexVersion.equals(chunk.getIndexVersion()))
                    .toList();
        }

        @Override
        public int deleteByDocumentIdAndVersion(Long documentId, Integer indexVersion) {
            int before = chunks.size();
            chunks.removeIf(chunk -> documentId.equals(chunk.getDocumentId())
                    && indexVersion.equals(chunk.getIndexVersion()));
            return before - chunks.size();
        }

        @Override
        public List<DocumentChunkEntity> replaceChunks(Long documentId,
                                                       Long userId,
                                                       String text,
                                                       Integer indexVersion) {
            throw new UnsupportedOperationException("text replace is not used");
        }

        @Override
        public List<DocumentChunkEntity> replaceChunks(Long documentId,
                                                       Long userId,
                                                       List<DocumentChunkCandidate> candidates,
                                                       Integer indexVersion) {
            deleteByDocumentIdAndVersion(documentId, indexVersion);
            List<DocumentChunkEntity> saved = toEntities(documentId, userId, candidates, indexVersion);
            chunks.addAll(saved);
            return saved;
        }

        @Override
        public void markIndexed(List<DocumentChunkEntity> chunks) {
            chunks.forEach(chunk -> chunk.setIndexStatus(DocumentChunkIndexStatus.INDEXED));
        }

        @Override
        public void markFailed(List<DocumentChunkEntity> chunks) {
            chunks.forEach(chunk -> chunk.setIndexStatus(DocumentChunkIndexStatus.FAILED));
        }

        private List<DocumentChunkEntity> toEntities(Long documentId,
                                                     Long userId,
                                                     List<DocumentChunkCandidate> candidates,
                                                     Integer indexVersion) {
            List<DocumentChunkEntity> entities = new ArrayList<>();
            for (int i = 0; i < candidates.size(); i++) {
                DocumentChunkCandidate candidate = candidates.get(i);
                DocumentChunkEntity entity = new DocumentChunkEntity();
                entity.setId(900L + i);
                entity.setDocumentId(documentId);
                entity.setUserId(userId);
                entity.setChunkIndex(candidate.chunkIndex());
                entity.setContent(candidate.content());
                entity.setContentHash(candidate.contentHash());
                entity.setStartOffset(candidate.startOffset());
                entity.setEndOffset(candidate.endOffset());
                entity.setTokenCount(candidate.tokenCount());
                entity.setIndexStatus(DocumentChunkIndexStatus.PENDING);
                entity.setIndexVersion(indexVersion);
                entity.setCreateTime(LocalDateTime.now());
                entity.setUpdateTime(LocalDateTime.now());
                entities.add(entity);
            }
            return entities;
        }
    }
}
