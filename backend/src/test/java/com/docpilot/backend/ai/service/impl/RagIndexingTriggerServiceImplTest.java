package com.docpilot.backend.ai.service.impl;

import com.docpilot.backend.ai.entity.DocumentChunkEntity;
import com.docpilot.backend.ai.rag.ChunkingServiceImpl;
import com.docpilot.backend.ai.rag.DocumentChunkCandidate;
import com.docpilot.backend.ai.rag.DocumentChunkIndexStatus;
import com.docpilot.backend.ai.rag.MockEmbeddingProvider;
import com.docpilot.backend.ai.rag.RagEmbeddingProperties;
import com.docpilot.backend.ai.rag.RagIndexingRequest;
import com.docpilot.backend.ai.rag.RagIndexingResult;
import com.docpilot.backend.ai.rag.RagIndexingStatus;
import com.docpilot.backend.ai.rag.RagQaProperties;
import com.docpilot.backend.ai.rag.RagRetrievalQuery;
import com.docpilot.backend.ai.rag.RagRetrievalResult;
import com.docpilot.backend.ai.rag.RagVectorStoreProperties;
import com.docpilot.backend.ai.rag.vector.inmemory.InMemoryVectorStoreClient;
import com.docpilot.backend.ai.service.DocumentChunkService;
import com.docpilot.backend.ai.service.RagDocumentRetrievalService;
import com.docpilot.backend.ai.service.RagIndexingService;
import com.docpilot.backend.ai.service.RagScopeGuard;
import com.docpilot.backend.common.error.ErrorCode;
import com.docpilot.backend.common.exception.BusinessException;
import com.docpilot.backend.document.parser.BlockType;
import com.docpilot.backend.document.parser.DocumentBlock;
import com.docpilot.backend.document.parser.ParseResult;
import com.docpilot.backend.document.entity.Document;
import com.docpilot.backend.document.mapper.DocumentMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagIndexingTriggerServiceImplTest {

    @Test
    void shouldScheduleIndexingWithDefaultVersionAfterParseSuccess() {
        RagIndexingService indexingService = mock(RagIndexingService.class);
        when(indexingService.index(org.mockito.ArgumentMatchers.any())).thenReturn(new RagIndexingResult(
                RagIndexingStatus.SUCCESS,
                61L,
                7L,
                1,
                2,
                2,
                "index completed"
        ));
        RagIndexingTriggerServiceImpl triggerService = new RagIndexingTriggerServiceImpl(
                indexingService,
                Runnable::run
        );

        triggerService.triggerAfterParseSuccess(7L, 61L, "parsed content");

        ArgumentCaptor<RagIndexingRequest> captor = ArgumentCaptor.forClass(RagIndexingRequest.class);
        verify(indexingService).index(captor.capture());
        assertThat(captor.getValue().userId()).isEqualTo(7L);
        assertThat(captor.getValue().documentId()).isEqualTo(61L);
        assertThat(captor.getValue().text()).isEqualTo("parsed content");
        assertThat(captor.getValue().indexVersion()).isEqualTo(1);
        assertThat(captor.getValue().embeddingModel()).isBlank();
    }

    @Test
    void shouldAllowBlankTextToReachIndexingWorkflow() {
        RagIndexingService indexingService = mock(RagIndexingService.class);
        when(indexingService.index(org.mockito.ArgumentMatchers.any())).thenReturn(new RagIndexingResult(
                RagIndexingStatus.SKIPPED_EMPTY_TEXT,
                61L,
                7L,
                1,
                0,
                0,
                "blank text skipped"
        ));
        RagIndexingTriggerServiceImpl triggerService = new RagIndexingTriggerServiceImpl(
                indexingService,
                Runnable::run
        );

        triggerService.triggerAfterParseSuccess(7L, 61L, " ");

        ArgumentCaptor<RagIndexingRequest> captor = ArgumentCaptor.forClass(RagIndexingRequest.class);
        verify(indexingService).index(captor.capture());
        assertThat(captor.getValue().text()).isEqualTo(" ");
    }

    @Test
    void shouldSkipWhenUserOrDocumentMissing() {
        RagIndexingService indexingService = mock(RagIndexingService.class);
        RagIndexingTriggerServiceImpl triggerService = new RagIndexingTriggerServiceImpl(
                indexingService,
                Runnable::run
        );

        triggerService.triggerAfterParseSuccess(null, 61L, "parsed content");
        triggerService.triggerAfterParseSuccess(7L, null, "parsed content");

        verify(indexingService, never()).index(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldNotThrowWhenIndexingFailsOrSchedulingFails() {
        RagIndexingService indexingService = mock(RagIndexingService.class);
        when(indexingService.index(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new IllegalStateException("provider endpoint should not leak"));
        RagIndexingTriggerServiceImpl triggerService = new RagIndexingTriggerServiceImpl(
                indexingService,
                Runnable::run
        );

        assertThatCode(() -> triggerService.triggerAfterParseSuccess(7L, 61L, "PRIVATE_DOC_BODY_MARKER"))
                .doesNotThrowAnyException();

        Executor throwingExecutor = command -> {
            throw new IllegalStateException("executor down");
        };
        RagIndexingTriggerServiceImpl schedulingFailureService = new RagIndexingTriggerServiceImpl(
                indexingService,
                throwingExecutor
        );
        assertThatCode(() -> schedulingFailureService.triggerAfterParseSuccess(7L, 61L, "PRIVATE_DOC_BODY_MARKER"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldSkipIndexingWhenScopeGuardRejectsDocument() {
        RagIndexingService indexingService = mock(RagIndexingService.class);
        RagScopeGuard ragScopeGuard = mock(RagScopeGuard.class);
        doThrow(new BusinessException(ErrorCode.DOCUMENT_FORBIDDEN))
                .when(ragScopeGuard).requireOwnedDocument(7L, 61L);
        RagIndexingTriggerServiceImpl triggerService = new RagIndexingTriggerServiceImpl(
                indexingService,
                ragScopeGuard,
                Runnable::run
        );

        assertThatCode(() -> triggerService.triggerAfterParseSuccess(7L, 61L, "parsed content"))
                .doesNotThrowAnyException();

        verify(indexingService, never()).index(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldIndexParsedContentForRetrievalWhenTriggeredAfterParseSuccess() {
        DocumentMapper documentMapper = mock(DocumentMapper.class);
        Document document = new Document();
        document.setId(61L);
        document.setUserId(7L);
        when(documentMapper.selectById(61L)).thenReturn(document);
        MockEmbeddingProvider embeddingProvider = new MockEmbeddingProvider(64, "mock-t008");
        InMemoryVectorStoreClient vectorStoreClient = new InMemoryVectorStoreClient();
        RagIndexingServiceImpl indexingService = new RagIndexingServiceImpl(
                new ChunkingServiceImpl(),
                new InMemoryDocumentChunkService(),
                embeddingProvider,
                vectorStoreClient,
                new RagEmbeddingProperties(),
                new RagVectorStoreProperties()
        );
        RagIndexingTriggerServiceImpl triggerService = new RagIndexingTriggerServiceImpl(
                indexingService,
                new RagScopeGuard(documentMapper),
                Runnable::run
        );
        RagDocumentRetrievalService retrievalService = new RagDocumentRetrievalServiceImpl(
                documentMapper,
                embeddingProvider,
                vectorStoreClient,
                new RagEmbeddingProperties(),
                new RagQaProperties()
        );

        triggerService.triggerAfterParseSuccess(7L, 61L, "Redis cache parse success evidence for T008.");
        RagRetrievalResult result = retrievalService.retrieve(new RagRetrievalQuery(
                7L,
                61L,
                "Where is parse success cache evidence?",
                3,
                1,
                "mock-t008"
        ));

        assertThat(result.noEvidence()).isFalse();
        assertThat(result.hits()).anySatisfy(hit -> assertThat(hit.content()).contains("Redis cache"));
        assertThat(result.citations()).isNotEmpty();
        assertThat(result.citations().get(0).index()).isEqualTo(result.hits().get(0).citationIndex());
    }

    @Test
    void shouldCarryParserBlockLocatorThroughMultiBlockIndexingToRetrievalCitations() {
        DocumentMapper documentMapper = mock(DocumentMapper.class);
        Document document = new Document();
        document.setId(61L);
        document.setUserId(7L);
        when(documentMapper.selectById(61L)).thenReturn(document);
        MockEmbeddingProvider embeddingProvider = new MockEmbeddingProvider(64, "mock-parser-contract");
        InMemoryVectorStoreClient vectorStoreClient = new InMemoryVectorStoreClient();
        RagIndexingServiceImpl indexingService = new RagIndexingServiceImpl(
                new ChunkingServiceImpl(),
                new InMemoryDocumentChunkService(),
                embeddingProvider,
                vectorStoreClient,
                new RagEmbeddingProperties(),
                new RagVectorStoreProperties()
        );
        RagIndexingTriggerServiceImpl triggerService = new RagIndexingTriggerServiceImpl(
                indexingService,
                new RagScopeGuard(documentMapper),
                Runnable::run
        );
        RagDocumentRetrievalService retrievalService = new RagDocumentRetrievalServiceImpl(
                documentMapper,
                embeddingProvider,
                vectorStoreClient,
                new RagEmbeddingProperties(),
                new RagQaProperties()
        );
        String pageOne = "# Parser Evidence Page One\n\nPDF page one citation marker alpha. " + "alpha detail ".repeat(80);
        String pageTwo = "# Parser Evidence Page Two\n\nPDF page two citation marker beta. " + "beta detail ".repeat(80);
        String pageThree = "# Parser Evidence Page Three\n\nPDF page three citation marker gamma. " + "gamma detail ".repeat(80);
        String parsedText = pageOne + "\n\n" + pageTwo + "\n\n" + pageThree;
        int pageOneStart = 0;
        int pageOneEnd = pageOne.length();
        int pageTwoStart = pageOneEnd + 2;
        int pageTwoEnd = pageTwoStart + pageTwo.length();
        int pageThreeStart = pageTwoEnd + 2;
        int pageThreeEnd = pageThreeStart + pageThree.length();
        ParseResult parseResult = new ParseResult(
                61L,
                "parser-contract.pdf",
                "application/pdf",
                2048L,
                parsedText,
                List.of(
                        new DocumentBlock(
                                0,
                                BlockType.PAGE,
                                pageOne,
                                1,
                                "Parser Evidence Page One",
                                "Parser Evidence / Page One",
                                pageOneStart,
                                pageOneEnd,
                                "page:1"
                        ),
                        new DocumentBlock(
                                1,
                                BlockType.PAGE,
                                pageTwo,
                                2,
                                "Parser Evidence Page Two",
                                "Parser Evidence / Page Two",
                                pageTwoStart,
                                pageTwoEnd,
                                "page:2"
                        ),
                        new DocumentBlock(
                                2,
                                BlockType.PAGE,
                                pageThree,
                                3,
                                "Parser Evidence Page Three",
                                "Parser Evidence / Page Three",
                                pageThreeStart,
                                pageThreeEnd,
                                "page:3"
                        )
                ),
                Map.of("format", "pdf"),
                List.of(),
                "pdfbox",
                "1",
                1L,
                parsedText.length(),
                2,
                1
        );

        triggerService.triggerAfterParseSuccess(7L, 61L, parseResult);
        RagRetrievalResult result = retrievalService.retrieve(new RagRetrievalQuery(
                7L,
                61L,
                "Where are the PDF page citation marker alpha beta gamma details?",
                10,
                1,
                "mock-parser-contract"
        ));

        assertThat(result.noEvidence()).isFalse();
        assertThat(result.hits()).hasSizeGreaterThanOrEqualTo(3);
        assertThat(result.citations()).hasSameSizeAs(result.hits());
        assertThat(result.hits())
                .extracting(hit -> hit.sourceLocator())
                .contains("page:1", "page:2", "page:3");
        assertThat(result.hits()).allSatisfy(hit -> {
            assertThat(hit.sectionPath()).startsWith("Parser Evidence / Page");
            assertThat(hit.pageNumber()).isIn(1, 2, 3);
            assertThat(hit.sourceLocator()).startsWith("page:");
            assertThat(hit.blockType()).isEqualTo("PAGE");
        });
        assertThat(result.citations()).allSatisfy(citation -> {
            assertThat(citation.sectionPath()).startsWith("Parser Evidence / Page");
            assertThat(citation.pageNumber()).isIn(1, 2, 3);
            assertThat(citation.sourceLocator()).startsWith("page:");
            assertThat(citation.blockType()).isEqualTo("PAGE");
        });
    }

    private static final class InMemoryDocumentChunkService implements DocumentChunkService {

        private final List<DocumentChunkEntity> chunks = new ArrayList<>();
        private long nextId = 1_000L;

        @Override
        public List<DocumentChunkEntity> saveChunks(Long documentId,
                                                    Long userId,
                                                    List<DocumentChunkCandidate> chunks,
                                                    Integer indexVersion) {
            return toEntities(chunks, indexVersion);
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
            throw new UnsupportedOperationException("text replace is not used in T008 trigger smoke");
        }

        @Override
        public List<DocumentChunkEntity> replaceChunks(Long documentId,
                                                       Long userId,
                                                       List<DocumentChunkCandidate> candidates,
                                                       Integer indexVersion) {
            deleteByDocumentIdAndVersion(documentId, indexVersion);
            List<DocumentChunkEntity> saved = toEntities(candidates, indexVersion);
            chunks.addAll(saved);
            return saved;
        }

        @Override
        public void markIndexed(List<DocumentChunkEntity> chunks) {
            chunks.forEach(chunk -> {
                chunk.setIndexStatus(DocumentChunkIndexStatus.INDEXED);
                chunk.setUpdateTime(LocalDateTime.now());
            });
        }

        @Override
        public void markFailed(List<DocumentChunkEntity> chunks) {
            chunks.forEach(chunk -> {
                chunk.setIndexStatus(DocumentChunkIndexStatus.FAILED);
                chunk.setUpdateTime(LocalDateTime.now());
            });
        }

        private List<DocumentChunkEntity> toEntities(List<DocumentChunkCandidate> candidates, Integer indexVersion) {
            List<DocumentChunkEntity> result = new ArrayList<>();
            for (DocumentChunkCandidate candidate : candidates) {
                DocumentChunkEntity entity = new DocumentChunkEntity();
                entity.setId(nextId++);
                entity.setDocumentId(candidate.documentId());
                entity.setUserId(candidate.userId());
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
                result.add(entity);
            }
            return result;
        }
    }
}
