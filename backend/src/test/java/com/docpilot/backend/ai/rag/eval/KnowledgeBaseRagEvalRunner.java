package com.docpilot.backend.ai.rag.eval;

import com.docpilot.backend.ai.entity.DocumentChunkEntity;
import com.docpilot.backend.ai.rag.ChunkingServiceImpl;
import com.docpilot.backend.ai.rag.DocumentChunkIndexStatus;
import com.docpilot.backend.ai.rag.DocumentChunkCandidate;
import com.docpilot.backend.ai.rag.KnowledgeBaseRagEvidenceCitation;
import com.docpilot.backend.ai.rag.KnowledgeBaseRagQaAnswer;
import com.docpilot.backend.ai.rag.KnowledgeBaseRagQaQuery;
import com.docpilot.backend.ai.rag.KnowledgeBaseRagRetrievalHit;
import com.docpilot.backend.ai.rag.KnowledgeBaseRagRetrievalQuery;
import com.docpilot.backend.ai.rag.KnowledgeBaseRagRetrievalResult;
import com.docpilot.backend.ai.rag.MockEmbeddingProvider;
import com.docpilot.backend.ai.rag.RagEmbeddingProperties;
import com.docpilot.backend.ai.rag.RagIndexingRequest;
import com.docpilot.backend.ai.rag.RagQaProperties;
import com.docpilot.backend.ai.rag.RagRetrievalProperties;
import com.docpilot.backend.ai.rag.RagVectorStoreProperties;
import com.docpilot.backend.ai.rag.vector.inmemory.InMemoryVectorStoreClient;
import com.docpilot.backend.ai.service.AiAnswerService;
import com.docpilot.backend.ai.service.DocumentChunkService;
import com.docpilot.backend.ai.service.KnowledgeBaseRagRetrievalService;
import com.docpilot.backend.ai.service.impl.KnowledgeBaseRagQaServiceImpl;
import com.docpilot.backend.ai.service.impl.KnowledgeBaseRagRetrievalServiceImpl;
import com.docpilot.backend.ai.service.impl.RagIndexingServiceImpl;
import com.docpilot.backend.common.exception.BusinessException;
import com.docpilot.backend.document.entity.Document;
import com.docpilot.backend.document.mapper.DocumentMapper;
import com.docpilot.backend.knowledge.constant.KnowledgeBaseStatus;
import com.docpilot.backend.knowledge.entity.KnowledgeBase;
import com.docpilot.backend.knowledge.mapper.KnowledgeBaseDocumentMapper;
import com.docpilot.backend.knowledge.mapper.KnowledgeBaseMapper;
import com.docpilot.backend.knowledge.service.KnowledgeBaseScopeGuard;
import com.docpilot.backend.knowledge.vo.KnowledgeBaseDocumentResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class KnowledgeBaseRagEvalRunner {

    public static final String CASES_RESOURCE = "/rag/knowledge-base-rag-eval-cases.json";
    public static final Path DEFAULT_REPORT_PATH = Path.of(
            "target",
            "rag-eval",
            "knowledge-base-rag-eval-latest.json"
    );

    private static final String EMBEDDING_MODEL = "mock-t012";

    private final ObjectMapper objectMapper;

    public KnowledgeBaseRagEvalRunner() {
        this(new ObjectMapper());
    }

    public KnowledgeBaseRagEvalRunner(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
    }

    public List<KnowledgeBaseRagEvalCase> loadCases() throws IOException {
        try (InputStream inputStream = getClass().getResourceAsStream(CASES_RESOURCE)) {
            if (inputStream == null) {
                throw new IllegalStateException("knowledge-base RAG eval cases resource is missing");
            }
            return objectMapper.readValue(inputStream, new TypeReference<>() {
            });
        }
    }

    public KnowledgeBaseRagEvalResult evaluateDefaultCases() throws IOException {
        return evaluate(loadCases());
    }

    public KnowledgeBaseRagEvalResult evaluate(List<KnowledgeBaseRagEvalCase> cases) {
        List<KnowledgeBaseRagEvalCase> resolvedCases = cases == null ? List.of() : List.copyOf(cases);
        List<KnowledgeBaseRagEvalResult.CaseEvaluation> evaluations = new ArrayList<>();
        int modelCallCount = 0;
        int noEvidenceModelCallCount = 0;
        for (KnowledgeBaseRagEvalCase evalCase : resolvedCases) {
            Harness harness = Harness.create(evalCase);
            KnowledgeBaseRagEvalResult.CaseEvaluation evaluation = evaluateCase(evalCase, harness);
            modelCallCount += harness.answerCallCount();
            if (evaluation.modelCalledForNoEvidence()) {
                noEvidenceModelCallCount++;
            }
            evaluations.add(evaluation);
        }
        return new KnowledgeBaseRagEvalResult(
                "in_memory",
                MockEmbeddingProvider.PROVIDER,
                KnowledgeBaseRagEvalMetrics.from(evaluations),
                modelCallCount,
                noEvidenceModelCallCount,
                evaluations
        );
    }

    public void writeArtifact(KnowledgeBaseRagEvalResult result, Path path) throws IOException {
        Path resolvedPath = path == null ? DEFAULT_REPORT_PATH : path;
        Files.createDirectories(resolvedPath.getParent());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(resolvedPath.toFile(), result.toSafeMap());
    }

    private KnowledgeBaseRagEvalResult.CaseEvaluation evaluateCase(KnowledgeBaseRagEvalCase evalCase,
                                                                   Harness harness) {
        try {
            KnowledgeBaseRagRetrievalResult retrieval = harness.retrievalService.retrieve(retrievalQuery(evalCase));
            KnowledgeBaseRagQaAnswer answer = harness.qaService.answer(qaQuery(evalCase));
            return summarize(evalCase, retrieval, answer, harness);
        } catch (BusinessException ex) {
            return failed(evalCase, ex.getErrorCode().name());
        } catch (RuntimeException ex) {
            return failed(evalCase, ex.getClass().getSimpleName());
        }
    }

    private KnowledgeBaseRagEvalResult.CaseEvaluation summarize(KnowledgeBaseRagEvalCase evalCase,
                                                               KnowledgeBaseRagRetrievalResult retrieval,
                                                               KnowledgeBaseRagQaAnswer answer,
                                                               Harness harness) {
        List<KnowledgeBaseRagRetrievalHit> hits = retrieval.hits();
        List<KnowledgeBaseRagEvidenceCitation> citations = retrieval.citations();
        List<Long> retrievedDocumentIds = distinct(hits.stream().map(KnowledgeBaseRagRetrievalHit::documentId).toList());
        List<Long> citationDocumentIds = distinct(citations.stream().map(KnowledgeBaseRagEvidenceCitation::documentId).toList());
        Set<Long> expectedDocumentIds = new LinkedHashSet<>(evalCase.expectedDocumentIds());
        boolean hit = evalCase.expectedMarkers().stream()
                .allMatch(marker -> hits.stream().anyMatch(item -> item.content().contains(marker)));
        boolean documentHit = retrievedDocumentIds.containsAll(evalCase.expectedDocumentIds())
                && expectedDocumentIds.containsAll(retrievedDocumentIds)
                && evalCase.forbiddenDocumentIds().stream().noneMatch(retrievedDocumentIds::contains);
        boolean citationHit = citationDocumentIds.containsAll(evalCase.expectedDocumentIds())
                && expectedDocumentIds.containsAll(citationDocumentIds)
                && evalCase.expectedMarkers().stream()
                .allMatch(marker -> citations.stream().anyMatch(item -> item.snippet().contains(marker)))
                && citationsAlign(hits, citations);
        boolean noEvidenceHit = retrieval.noEvidence() && answer.noEvidence();
        boolean scopeViolation = scopeViolation(evalCase, hits, citations);
        boolean modelCalledForNoEvidence = evalCase.expectedNoEvidence() && harness.answerCallCount() > 0;
        boolean passed = evalCase.expectedNoEvidence()
                ? noEvidenceHit && !scopeViolation && !modelCalledForNoEvidence
                : hit && documentHit && citationHit && !retrieval.noEvidence() && !scopeViolation;
        return new KnowledgeBaseRagEvalResult.CaseEvaluation(
                evalCase.id(),
                evalCase.expectedNoEvidence(),
                hits.size(),
                citations.size(),
                retrievedDocumentIds,
                citationDocumentIds,
                evalCase.expectedDocumentIds(),
                hit,
                documentHit,
                citationHit,
                noEvidenceHit,
                scopeViolation,
                modelCalledForNoEvidence,
                passed,
                ""
        );
    }

    private KnowledgeBaseRagEvalResult.CaseEvaluation failed(KnowledgeBaseRagEvalCase evalCase, String errorType) {
        return new KnowledgeBaseRagEvalResult.CaseEvaluation(
                evalCase.id(),
                evalCase.expectedNoEvidence(),
                0,
                0,
                List.of(),
                List.of(),
                evalCase.expectedDocumentIds(),
                false,
                false,
                false,
                false,
                true,
                false,
                false,
                errorType
        );
    }

    private KnowledgeBaseRagRetrievalQuery retrievalQuery(KnowledgeBaseRagEvalCase evalCase) {
        return new KnowledgeBaseRagRetrievalQuery(
                evalCase.userId(),
                evalCase.knowledgeBaseId(),
                evalCase.query(),
                evalCase.topK(),
                evalCase.indexVersion(),
                EMBEDDING_MODEL
        );
    }

    private KnowledgeBaseRagQaQuery qaQuery(KnowledgeBaseRagEvalCase evalCase) {
        return new KnowledgeBaseRagQaQuery(
                evalCase.userId(),
                evalCase.knowledgeBaseId(),
                evalCase.query(),
                evalCase.topK(),
                evalCase.indexVersion(),
                "t012-eval"
        );
    }

    private boolean citationsAlign(List<KnowledgeBaseRagRetrievalHit> hits,
                                   List<KnowledgeBaseRagEvidenceCitation> citations) {
        if (hits.size() != citations.size()) {
            return false;
        }
        for (int i = 0; i < hits.size(); i++) {
            KnowledgeBaseRagRetrievalHit hit = hits.get(i);
            KnowledgeBaseRagEvidenceCitation citation = citations.get(i);
            if (hit.citationIndex() != citation.index()
                    || !hit.documentId().equals(citation.documentId())
                    || !hit.documentTitle().equals(citation.documentTitle())
                    || !hit.chunkIndex().equals(citation.chunkIndex())
                    || Double.compare(hit.score(), citation.score()) != 0
                    || !citation.snippet().contains(hit.snippet().replace("...", ""))) {
                return false;
            }
        }
        return true;
    }

    private boolean scopeViolation(KnowledgeBaseRagEvalCase evalCase,
                                   List<KnowledgeBaseRagRetrievalHit> hits,
                                   List<KnowledgeBaseRagEvidenceCitation> citations) {
        Set<Long> activeDocumentIds = new LinkedHashSet<>(evalCase.activeDocumentIds());
        boolean hitViolation = hits.stream().anyMatch(hit ->
                !evalCase.userId().equals(hit.userId())
                        || !activeDocumentIds.contains(hit.documentId())
                        || !evalCase.indexVersion().equals(hit.indexVersion())
                        || evalCase.forbiddenDocumentIds().contains(hit.documentId()));
        boolean citationViolation = citations.stream().anyMatch(citation ->
                !activeDocumentIds.contains(citation.documentId())
                        || !evalCase.indexVersion().equals(citation.indexVersion())
                        || evalCase.forbiddenDocumentIds().contains(citation.documentId()));
        return hitViolation || citationViolation;
    }

    private List<Long> distinct(List<Long> values) {
        return values.stream()
                .filter(value -> value != null && value > 0)
                .distinct()
                .toList();
    }

    private static final class Harness {

        private final Map<Long, KnowledgeBase> knowledgeBases = new LinkedHashMap<>();
        private final Map<Long, Document> documents = new LinkedHashMap<>();
        private final Map<String, List<KnowledgeBaseDocumentResponse>> activeDocuments = new LinkedHashMap<>();
        private final RagIndexingServiceImpl indexingService;
        private final KnowledgeBaseRagRetrievalService retrievalService;
        private final KnowledgeBaseRagQaServiceImpl qaService;
        private final AiAnswerService aiAnswerService;

        private Harness(RagIndexingServiceImpl indexingService,
                        KnowledgeBaseRagRetrievalService retrievalService,
                        KnowledgeBaseRagQaServiceImpl qaService,
                        AiAnswerService aiAnswerService) {
            this.indexingService = indexingService;
            this.retrievalService = retrievalService;
            this.qaService = qaService;
            this.aiAnswerService = aiAnswerService;
        }

        private static Harness create(KnowledgeBaseRagEvalCase evalCase) {
            KnowledgeBaseMapper knowledgeBaseMapper = mock(KnowledgeBaseMapper.class);
            KnowledgeBaseDocumentMapper knowledgeBaseDocumentMapper = mock(KnowledgeBaseDocumentMapper.class);
            DocumentMapper documentMapper = mock(DocumentMapper.class);
            InMemoryDocumentChunkService chunkService = new InMemoryDocumentChunkService();
            MockEmbeddingProvider embeddingProvider = new MockEmbeddingProvider(256, EMBEDDING_MODEL);
            InMemoryVectorStoreClient vectorStoreClient = new InMemoryVectorStoreClient();
            RagEmbeddingProperties embeddingProperties = new RagEmbeddingProperties();
            RagQaProperties qaProperties = new RagQaProperties();
            qaProperties.setTopK(3);
            qaProperties.setFallbackEnabled(true);
            qaProperties.setMaxContextChars(4096);
            RagIndexingServiceImpl indexingService = new RagIndexingServiceImpl(
                    new ChunkingServiceImpl(),
                    chunkService,
                    embeddingProvider,
                    vectorStoreClient,
                    embeddingProperties,
                    new RagVectorStoreProperties()
            );
            KnowledgeBaseScopeGuard scopeGuard = new KnowledgeBaseScopeGuard(
                    knowledgeBaseMapper,
                    knowledgeBaseDocumentMapper,
                    documentMapper
            );
            KnowledgeBaseRagRetrievalService retrievalService = new KnowledgeBaseRagRetrievalServiceImpl(
                    scopeGuard,
                    embeddingProvider,
                    vectorStoreClient,
                    null, // hybridRetrievalService - not needed for eval
                    embeddingProperties,
                    qaProperties,
                    new RagRetrievalProperties(), // default retrieval properties
                    null,
                    null
            );
            AiAnswerService aiAnswerService = mock(AiAnswerService.class);
            when(aiAnswerService.answer(any(), any())).thenReturn("Synthetic eval answer. [1]");
            KnowledgeBaseRagQaServiceImpl qaService = new KnowledgeBaseRagQaServiceImpl(
                    retrievalService,
                    aiAnswerService,
                    qaProperties
            );
            Harness harness = new Harness(indexingService, retrievalService, qaService, aiAnswerService);
            when(knowledgeBaseMapper.selectById(any())).thenAnswer(invocation ->
                    harness.knowledgeBases.get(((Number) invocation.getArgument(0)).longValue()));
            when(knowledgeBaseDocumentMapper.selectActiveDocumentResponses(any(), any())).thenAnswer(invocation ->
                    harness.activeDocuments.getOrDefault(
                            key(((Number) invocation.getArgument(0)).longValue(),
                                    ((Number) invocation.getArgument(1)).longValue()),
                            List.of()
                    ));
            when(documentMapper.selectById(any())).thenAnswer(invocation ->
                    harness.documents.get(((Number) invocation.getArgument(0)).longValue()));
            harness.register(evalCase);
            return harness;
        }

        private void register(KnowledgeBaseRagEvalCase evalCase) {
            KnowledgeBase knowledgeBase = new KnowledgeBase();
            knowledgeBase.setId(evalCase.knowledgeBaseId());
            knowledgeBase.setUserId(evalCase.userId());
            knowledgeBase.setName("T012 KB " + evalCase.id());
            knowledgeBase.setStatus(KnowledgeBaseStatus.ACTIVE);
            knowledgeBases.put(evalCase.knowledgeBaseId(), knowledgeBase);

            List<KnowledgeBaseDocumentResponse> responses = new ArrayList<>();
            for (KnowledgeBaseRagEvalCase.EvalDocument document : evalCase.documents()) {
                registerDocument(evalCase, document, true, responses);
            }
            activeDocuments.put(key(evalCase.userId(), evalCase.knowledgeBaseId()), List.copyOf(responses));
            for (KnowledgeBaseRagEvalCase.EvalDocument document : evalCase.outOfScopeDocuments()) {
                registerDocument(evalCase, document, false, responses);
            }
        }

        private void registerDocument(KnowledgeBaseRagEvalCase evalCase,
                                      KnowledgeBaseRagEvalCase.EvalDocument evalDocument,
                                      boolean active,
                                      List<KnowledgeBaseDocumentResponse> responses) {
            Long userId = evalDocument.userId() == null ? evalCase.userId() : evalDocument.userId();
            Integer indexVersion = evalDocument.indexVersion() == null
                    ? evalCase.indexVersion()
                    : evalDocument.indexVersion();
            Document document = new Document();
            document.setId(evalDocument.documentId());
            document.setUserId(userId);
            document.setTitle(evalDocument.title());
            document.setParseStatus("SUCCESS");
            documents.put(evalDocument.documentId(), document);
            if (active) {
                KnowledgeBaseDocumentResponse response = new KnowledgeBaseDocumentResponse();
                response.setId(10_000L + evalDocument.documentId());
                response.setKnowledgeBaseId(evalCase.knowledgeBaseId());
                response.setDocumentId(evalDocument.documentId());
                response.setDocumentTitle(evalDocument.title());
                response.setParseStatus("SUCCESS");
                response.setStatus(KnowledgeBaseStatus.ACTIVE);
                response.setCreateTime(LocalDateTime.now());
                response.setUpdateTime(LocalDateTime.now());
                responses.add(response);
            }
            indexingService.index(new RagIndexingRequest(
                    evalDocument.documentId(),
                    userId,
                    evalDocument.text(),
                    indexVersion,
                    EMBEDDING_MODEL
            ));
        }

        private int answerCallCount() {
            return (int) Mockito.mockingDetails(aiAnswerService)
                    .getInvocations()
                    .stream()
                    .filter(invocation -> "answer".equals(invocation.getMethod().getName()))
                    .count();
        }

        private static String key(Long userId, Long knowledgeBaseId) {
            return userId + ":" + knowledgeBaseId;
        }
    }

    private static final class InMemoryDocumentChunkService implements DocumentChunkService {

        private final List<DocumentChunkEntity> chunks = new ArrayList<>();
        private long nextId = 20_000L;

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
            throw new UnsupportedOperationException("text replace is not used in T012 eval");
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
