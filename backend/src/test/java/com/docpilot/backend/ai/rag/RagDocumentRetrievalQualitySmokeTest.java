package com.docpilot.backend.ai.rag;

import com.docpilot.backend.ai.entity.DocumentChunkEntity;
import com.docpilot.backend.ai.entity.DocumentQaHistory;
import com.docpilot.backend.ai.mapper.DocumentQaHistoryMapper;
import com.docpilot.backend.ai.rag.vector.inmemory.InMemoryVectorStoreClient;
import com.docpilot.backend.ai.service.AiAnswerService;
import com.docpilot.backend.ai.service.DocumentChunkService;
import com.docpilot.backend.ai.service.RagDocumentRetrievalService;
import com.docpilot.backend.ai.service.RagScopeGuard;
import com.docpilot.backend.ai.service.impl.RagDocumentRetrievalServiceImpl;
import com.docpilot.backend.ai.service.impl.RagIndexingServiceImpl;
import com.docpilot.backend.ai.service.impl.RagQaServiceImpl;
import com.docpilot.backend.document.entity.Document;
import com.docpilot.backend.document.mapper.DocumentMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagDocumentRetrievalQualitySmokeTest {

    private static final String CASES_RESOURCE = "/rag/rag-document-retrieval-smoke-cases.json";
    private static final Path REPORT_PATH = Path.of("target", "rag-eval", "rag-document-retrieval-quality-smoke.json");
    private static final String PRIVATE_DOC_MARKER = "PRIVATE_T007_RAG_DOC_MARKER_DO_NOT_DUMP";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldEvaluateNewRetrievalWorkflowWithInMemoryStore() throws Exception {
        SmokeHarness harness = SmokeHarness.create();
        List<SmokeCase> cases = loadCases();
        harness.registerDocuments(cases);
        harness.indexCases(cases);

        List<CaseSummary> summaries = new ArrayList<>();
        int positiveCases = 0;
        int positiveHits = 0;
        int positiveCitationHits = 0;
        int noEvidenceCases = 0;
        int noEvidenceHits = 0;

        for (SmokeCase smokeCase : cases) {
            harness.retrievalProperties.setMinSimilarityThreshold(smokeCase.minSimilarityThreshold());
            RagRetrievalResult result = harness.retrievalService.retrieve(new RagRetrievalQuery(
                    smokeCase.userId(),
                    smokeCase.documentId(),
                    smokeCase.query(),
                    smokeCase.topK(),
                    smokeCase.indexVersion(),
                    "mock-t007"
            ));
            boolean hit = containsMarker(result.hits(), smokeCase.expectedMarker());
            boolean citationHit = containsMarkerInCitations(result.citations(), smokeCase.expectedMarker())
                    && citationIndexesAlign(result);
            boolean forbiddenHit = !smokeCase.forbiddenMarker().isBlank()
                    && (containsMarker(result.hits(), smokeCase.forbiddenMarker())
                    || containsMarkerInCitations(result.citations(), smokeCase.forbiddenMarker()));
            if (smokeCase.expectedHit()) {
                positiveCases++;
                if (hit) {
                    positiveHits++;
                }
                if (citationHit) {
                    positiveCitationHits++;
                }
            }
            if (smokeCase.expectedNoEvidence()) {
                noEvidenceCases++;
                if (result.noEvidence()) {
                    noEvidenceHits++;
                }
            }
            summaries.add(new CaseSummary(
                    smokeCase.id(),
                    smokeCase.expectedHit(),
                    smokeCase.expectedNoEvidence(),
                    result.hits().size(),
                    result.citations().size(),
                    hit,
                    citationHit,
                    forbiddenHit,
                    result.noEvidence(),
                    smokeCase.expectedHit() == hit
                            && !forbiddenHit
                            && smokeCase.expectedNoEvidence() == result.noEvidence()
            ));
        }

        QualitySummary summary = new QualitySummary(
                "in_memory",
                MockEmbeddingProvider.PROVIDER,
                cases.size(),
                positiveCases,
                rate(positiveHits, positiveCases),
                rate(positiveCitationHits, positiveCases),
                rate(noEvidenceHits, noEvidenceCases),
                metadataIsolationPassRate(harness),
                summaries
        );

        Files.createDirectories(REPORT_PATH.getParent());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(REPORT_PATH.toFile(), summary.toSafeMap());
        String report = Files.readString(REPORT_PATH, StandardCharsets.UTF_8);

        assertThat(summary.hitAtK()).isEqualTo(1.0D);
        assertThat(summary.citationHitRate()).isEqualTo(1.0D);
        assertThat(summary.noEvidenceRate()).isEqualTo(1.0D);
        assertThat(summary.metadataIsolationPassRate()).isEqualTo(1.0D);
        assertThat(summary.caseSummaries()).allSatisfy(caseSummary -> assertThat(caseSummary.passed()).isTrue());
        assertThat(report)
                .contains("\"provider\" : \"in_memory\"")
                .contains("\"embeddingProvider\" : \"mock\"")
                .contains("\"hitAtK\" : \"1.0000\"")
                .doesNotContain(PRIVATE_DOC_MARKER)
                .doesNotContain("documentText")
                .doesNotContain("prompt")
                .doesNotContain("secret")
                .doesNotContain("Authorization")
                .doesNotContain("apiKey");
    }

    @Test
    void shouldKeepQaFallbackOfflineAndAvoidCallingModelWithoutEvidence() throws Exception {
        SmokeHarness harness = SmokeHarness.create();
        List<SmokeCase> cases = loadCases();
        harness.registerDocuments(cases);
        harness.indexCases(cases);
        when(harness.aiAnswerService.answer(any(), any())).thenReturn("DocPilot stores cache state in Redis. [1]");

        SmokeCase hitCase = cases.stream()
                .filter(SmokeCase::expectedHit)
                .findFirst()
                .orElseThrow();
        harness.retrievalProperties.setMinSimilarityThreshold(hitCase.minSimilarityThreshold());
        RagQaAnswer answer = harness.qaService.answer(new RagQaQuery(
                hitCase.userId(),
                hitCase.documentId(),
                hitCase.query(),
                hitCase.topK(),
                hitCase.indexVersion(),
                "t007-hit"
        ));

        assertThat(answer.noEvidence()).isFalse();
        assertThat(answer.answer()).contains("[1]");
        assertThat(answer.retrieval().citations()).isNotEmpty();
        verify(harness.aiAnswerService).answer(any(), any());
        verify(harness.documentQaHistoryMapper).insert(any(DocumentQaHistory.class));

        SmokeCase noEvidenceCase = cases.stream()
                .filter(SmokeCase::expectedNoEvidence)
                .findFirst()
                .orElseThrow();
        harness.retrievalProperties.setMinSimilarityThreshold(noEvidenceCase.minSimilarityThreshold());
        RagQaAnswer noEvidence = harness.qaService.answer(new RagQaQuery(
                noEvidenceCase.userId(),
                noEvidenceCase.documentId(),
                noEvidenceCase.query(),
                noEvidenceCase.topK(),
                noEvidenceCase.indexVersion(),
                "t007-no-evidence"
        ));

        assertThat(noEvidence.noEvidence()).isTrue();
        assertThat(noEvidence.fallbackReason()).isEqualTo("no_evidence");
        assertThat(noEvidence.answer()).contains("未在当前文档索引中检索到足够证据");
        verify(harness.aiAnswerService, org.mockito.Mockito.times(1)).answer(any(), any());
        verify(harness.aiAnswerService, never()).streamAnswer(any(), any(), any());
    }

    private double metadataIsolationPassRate(SmokeHarness harness) {
        harness.registerDocument(8101L, 201L);
        harness.registerDocument(8102L, 202L);
        harness.index(201L, 8101L, 1, "Scoped Redis evidence belongs to document A.");
        harness.index(202L, 8102L, 1, "metadata-isolation-marker belongs to document B only.");
        RagRetrievalResult documentScoped = harness.retrievalService.retrieve(new RagRetrievalQuery(
                201L,
                8101L,
                "metadata-isolation-marker",
                5,
                1,
                "mock-t007"
        ));

        harness.registerDocument(8103L, 203L);
        harness.index(203L, 8103L, 1, "version-one-marker belongs to index version one.");
        harness.index(203L, 8103L, 2, "version-two-marker belongs to index version two.");
        RagRetrievalResult versionOne = harness.retrievalService.retrieve(new RagRetrievalQuery(
                203L,
                8103L,
                "version-two-marker",
                5,
                1,
                "mock-t007"
        ));
        RagRetrievalResult versionTwo = harness.retrievalService.retrieve(new RagRetrievalQuery(
                203L,
                8103L,
                "version-two-marker",
                5,
                2,
                "mock-t007"
        ));

        boolean documentIsolation = documentScoped.hits().stream()
                .noneMatch(hit -> hit.content().contains("metadata-isolation-marker"));
        boolean versionIsolation = versionOne.hits().stream()
                .noneMatch(hit -> hit.content().contains("version-two-marker"))
                && versionTwo.hits().stream().anyMatch(hit -> hit.content().contains("version-two-marker"));
        return documentIsolation && versionIsolation ? 1.0D : 0.0D;
    }

    private List<SmokeCase> loadCases() throws Exception {
        try (InputStream inputStream = getClass().getResourceAsStream(CASES_RESOURCE)) {
            if (inputStream == null) {
                throw new IllegalStateException("RAG document retrieval smoke cases resource is missing.");
            }
            return objectMapper.readValue(inputStream, new TypeReference<>() {
            });
        }
    }

    private boolean containsMarker(List<RagRetrievalHit> hits, String marker) {
        return hits.stream().anyMatch(hit -> hit.content().contains(marker));
    }

    private boolean containsMarkerInCitations(List<RagEvidenceCitation> citations, String marker) {
        return citations.stream().anyMatch(citation -> citation.snippet().contains(marker));
    }

    private boolean citationIndexesAlign(RagRetrievalResult result) {
        if (result.hits().size() != result.citations().size()) {
            return false;
        }
        for (int i = 0; i < result.hits().size(); i++) {
            if (result.hits().get(i).citationIndex() != result.citations().get(i).index()) {
                return false;
            }
        }
        return true;
    }

    private double rate(int numerator, int denominator) {
        return denominator == 0 ? 1.0D : (double) numerator / denominator;
    }

    private record SmokeCase(
            String id,
            Long userId,
            Long documentId,
            Integer indexVersion,
            String documentText,
            String query,
            Integer topK,
            String expectedMarker,
            String forbiddenMarker,
            boolean expectedHit,
            boolean expectedNoEvidence,
            Double minSimilarityThreshold
    ) {
        SmokeCase {
            id = id == null ? "" : id.trim();
            documentText = documentText == null ? "" : documentText;
            query = query == null ? "" : query.trim();
            expectedMarker = expectedMarker == null ? "" : expectedMarker.trim();
            forbiddenMarker = forbiddenMarker == null ? "" : forbiddenMarker.trim();
            minSimilarityThreshold = minSimilarityThreshold == null ? 0.0D : minSimilarityThreshold;
            if (minSimilarityThreshold < 0.0D || minSimilarityThreshold > 1.0D) {
                throw new IllegalArgumentException("minSimilarityThreshold must be between 0 and 1");
            }
        }
    }

    private record CaseSummary(
            String id,
            boolean expectedHit,
            boolean expectedNoEvidence,
            int retrievedCount,
            int citationCount,
            boolean hit,
            boolean citationHit,
            boolean forbiddenHit,
            boolean noEvidence,
            boolean passed
    ) {
        Map<String, Object> toSafeMap() {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("id", id);
            value.put("expectedHit", expectedHit);
            value.put("expectedNoEvidence", expectedNoEvidence);
            value.put("retrievedCount", retrievedCount);
            value.put("citationCount", citationCount);
            value.put("hit", hit);
            value.put("citationHit", citationHit);
            value.put("forbiddenHit", forbiddenHit);
            value.put("noEvidence", noEvidence);
            value.put("passed", passed);
            return value;
        }
    }

    private record QualitySummary(
            String provider,
            String embeddingProvider,
            int totalCases,
            int positiveCases,
            double hitAtK,
            double citationHitRate,
            double noEvidenceRate,
            double metadataIsolationPassRate,
            List<CaseSummary> caseSummaries
    ) {
        Map<String, Object> toSafeMap() {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("provider", provider);
            value.put("embeddingProvider", embeddingProvider);
            value.put("totalCases", totalCases);
            value.put("positiveCases", positiveCases);
            value.put("hitAtK", format(hitAtK));
            value.put("citationHitRate", format(citationHitRate));
            value.put("noEvidenceRate", format(noEvidenceRate));
            value.put("metadataIsolationPassRate", format(metadataIsolationPassRate));
            value.put("caseSummaries", caseSummaries.stream().map(CaseSummary::toSafeMap).toList());
            value.put("notes", List.of(
                    "Synthetic smoke cases only",
                    "No document text or model inputs are stored",
                    "Uses MockEmbeddingProvider and InMemoryVectorStoreClient"
            ));
            return value;
        }

        private String format(double value) {
            return String.format(java.util.Locale.ROOT, "%.4f", value);
        }
    }

    private static final class SmokeHarness {

        private final DocumentMapper documentMapper;
        private final AiAnswerService aiAnswerService;
        private final DocumentQaHistoryMapper documentQaHistoryMapper;
        private final InMemoryDocumentChunkService chunkService;
        private final RagRetrievalProperties retrievalProperties;
        private final RagIndexingServiceImpl indexingService;
        private final RagDocumentRetrievalService retrievalService;
        private final RagQaServiceImpl qaService;
        private final Map<Long, Document> documents = new LinkedHashMap<>();

        private SmokeHarness(DocumentMapper documentMapper,
                             AiAnswerService aiAnswerService,
                             DocumentQaHistoryMapper documentQaHistoryMapper,
                             InMemoryDocumentChunkService chunkService,
                             RagRetrievalProperties retrievalProperties,
                             RagIndexingServiceImpl indexingService,
                             RagDocumentRetrievalService retrievalService,
                             RagQaServiceImpl qaService) {
            this.documentMapper = documentMapper;
            this.aiAnswerService = aiAnswerService;
            this.documentQaHistoryMapper = documentQaHistoryMapper;
            this.chunkService = chunkService;
            this.retrievalProperties = retrievalProperties;
            this.indexingService = indexingService;
            this.retrievalService = retrievalService;
            this.qaService = qaService;
        }

        private static SmokeHarness create() {
            DocumentMapper documentMapper = mock(DocumentMapper.class);
            AiAnswerService aiAnswerService = mock(AiAnswerService.class);
            DocumentQaHistoryMapper historyMapper = mock(DocumentQaHistoryMapper.class);
            InMemoryDocumentChunkService chunkService = new InMemoryDocumentChunkService();
            MockEmbeddingProvider embeddingProvider = new MockEmbeddingProvider(64, "mock-t007");
            InMemoryVectorStoreClient vectorStoreClient = new InMemoryVectorStoreClient();
            RagEmbeddingProperties embeddingProperties = new RagEmbeddingProperties();
            RagQaProperties qaProperties = new RagQaProperties();
            qaProperties.setTopK(3);
            qaProperties.setFallbackEnabled(true);
            RagRetrievalProperties retrievalProperties = new RagRetrievalProperties();

            RagIndexingServiceImpl indexingService = new RagIndexingServiceImpl(
                    new ChunkingServiceImpl(),
                    chunkService,
                    embeddingProvider,
                    vectorStoreClient,
                    embeddingProperties,
                    new RagVectorStoreProperties()
            );
            RagDocumentRetrievalService retrievalService = new RagDocumentRetrievalServiceImpl(
                    documentMapper,
                    embeddingProvider,
                    vectorStoreClient,
                    embeddingProperties,
                    qaProperties,
                    retrievalProperties,
                    new RagScopeGuard(documentMapper)
            );
            RagQaServiceImpl qaService = new RagQaServiceImpl(
                    retrievalService,
                    aiAnswerService,
                    historyMapper,
                    qaProperties
            );
            SmokeHarness harness = new SmokeHarness(
                    documentMapper,
                    aiAnswerService,
                    historyMapper,
                    chunkService,
                    retrievalProperties,
                    indexingService,
                    retrievalService,
                    qaService
            );
            when(documentMapper.selectById(any())).thenAnswer(invocation ->
                    harness.documents.get(((Number) invocation.getArgument(0)).longValue()));
            return harness;
        }

        private void registerDocuments(List<SmokeCase> cases) {
            cases.forEach(smokeCase -> registerDocument(smokeCase.documentId(), smokeCase.userId()));
        }

        private void registerDocument(Long documentId, Long userId) {
            Document document = new Document();
            document.setId(documentId);
            document.setUserId(userId);
            documents.put(documentId, document);
        }

        private void indexCases(List<SmokeCase> cases) {
            cases.forEach(smokeCase -> index(
                    smokeCase.userId(),
                    smokeCase.documentId(),
                    smokeCase.indexVersion(),
                    smokeCase.documentText()
            ));
        }

        private void index(Long userId, Long documentId, Integer indexVersion, String text) {
            indexingService.index(new RagIndexingRequest(
                    documentId,
                    userId,
                    text == null || text.isBlank() ? text : text + " " + PRIVATE_DOC_MARKER,
                    indexVersion,
                    "mock-t007"
            ));
        }
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
            throw new UnsupportedOperationException("text replace is not used in T007 smoke");
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
