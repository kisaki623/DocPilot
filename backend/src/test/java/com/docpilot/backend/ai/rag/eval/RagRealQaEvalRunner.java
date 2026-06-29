package com.docpilot.backend.ai.rag.eval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RagRealQaEvalRunner {

    public static final String CASES_RESOURCE = "/rag/real-qa-eval-cases.json";
    public static final Path DEFAULT_REPORT_PATH = Path.of("target", "rag-eval", "real-qa-eval-latest.json");

    private final ObjectMapper objectMapper;
    private final KnowledgeBaseRagEvalRunner delegate;

    public RagRealQaEvalRunner() {
        this(new ObjectMapper());
    }

    public RagRealQaEvalRunner(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
        this.delegate = new KnowledgeBaseRagEvalRunner(this.objectMapper);
    }

    public List<RagRealQaEvalCase> loadCases() throws IOException {
        try (InputStream inputStream = getClass().getResourceAsStream(CASES_RESOURCE)) {
            if (inputStream == null) {
                throw new IllegalStateException("real QA eval cases resource is missing");
            }
            return objectMapper.readValue(inputStream, new TypeReference<>() {
            });
        }
    }

    public RagRealQaEvalResult evaluateDefaultCases() throws IOException {
        return evaluate(loadCases());
    }

    public RagRealQaEvalResult evaluate(List<RagRealQaEvalCase> cases) {
        List<RagRealQaEvalCase> resolvedCases = cases == null ? List.of() : List.copyOf(cases);
        KnowledgeBaseRagEvalResult delegateResult = delegate.evaluate(resolvedCases.stream()
                .map(this::toKnowledgeBaseCase)
                .toList());
        Map<String, RagRealQaEvalCase> caseById = resolvedCases.stream()
                .collect(LinkedHashMap::new, (map, item) -> map.put(item.id(), item), LinkedHashMap::putAll);
        List<RagRealQaEvalResult.CaseEvaluation> evaluations = delegateResult.caseEvaluations().stream()
                .map(evaluation -> summarize(caseById.get(evaluation.id()), evaluation))
                .toList();
        return new RagRealQaEvalResult(
                delegateResult.provider(),
                delegateResult.embeddingProvider(),
                RagRealQaEvalMetrics.from(evaluations),
                evaluations,
                delegateResult.retrievalModeMetrics()
        );
    }

    public void writeArtifact(RagRealQaEvalResult result, Path path) throws IOException {
        Path resolvedPath = path == null ? DEFAULT_REPORT_PATH : path;
        Files.createDirectories(resolvedPath.getParent());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(resolvedPath.toFile(), result.toSafeMap());
    }

    private KnowledgeBaseRagEvalCase toKnowledgeBaseCase(RagRealQaEvalCase evalCase) {
        return new KnowledgeBaseRagEvalCase(
                evalCase.id(),
                evalCase.userId(),
                evalCase.knowledgeBaseId(),
                evalCase.indexVersion(),
                evalCase.topK(),
                evalCase.query(),
                evalCase.documents().stream().map(this::toKnowledgeBaseDocument).toList(),
                evalCase.outOfScopeDocuments().stream().map(this::toKnowledgeBaseDocument).toList(),
                evalCase.requiredCitationMarkers(),
                evalCase.expectedAnswerMarkers(),
                evalCase.forbiddenAnswerMarkers(),
                evalCase.expectedDocumentIds(),
                evalCase.forbiddenDocumentIds(),
                evalCase.minCitationCount(),
                evalCase.minDocumentCoverage() > 1,
                evalCase.expectedNoEvidence(),
                evalCase.minSimilarityThreshold()
        );
    }

    private KnowledgeBaseRagEvalCase.EvalDocument toKnowledgeBaseDocument(RagRealQaEvalCase.EvalDocument document) {
        return new KnowledgeBaseRagEvalCase.EvalDocument(
                document.userId(),
                document.documentId(),
                document.title(),
                document.indexVersion(),
                document.text()
        );
    }

    private RagRealQaEvalResult.CaseEvaluation summarize(RagRealQaEvalCase evalCase,
                                                         KnowledgeBaseRagEvalResult.CaseEvaluation evaluation) {
        boolean citationGrounded = evaluation.citationHit() && evaluation.groundedAnswerHit();
        boolean coverageHit = evaluation.citationDocumentIds().size() >= evalCase.minDocumentCoverage();
        List<String> failureReasons = new ArrayList<>(evaluation.failureReasons());
        if (!coverageHit) {
            failureReasons.add("document_coverage_miss");
        }
        RagClaimSupportScore claimSupport = RagClaimSupportScorer.score(evalCase, evaluation);
        if (claimSupport.required() && !claimSupport.claimSupportHit()) {
            failureReasons.add("claim_support_miss");
        }
        if (claimSupport.forbiddenClaimHit()) {
            failureReasons.add("forbidden_claim_leak");
        }
        boolean passed = evaluation.passed() && coverageHit
                && (!claimSupport.required() || claimSupport.claimSupportHit());
        return new RagRealQaEvalResult.CaseEvaluation(
                evalCase.id(),
                evalCase.category(),
                evalCase.retrievalMode(),
                evalCase.expectedNoEvidence(),
                evaluation.retrievedCount(),
                evaluation.citationCount(),
                evaluation.retrievedDocumentIds(),
                evaluation.citationDocumentIds(),
                evalCase.expectedDocumentIds(),
                evalCase.minDocumentCoverage(),
                evaluation.answerHit(),
                citationGrounded,
                coverageHit,
                evaluation.noEvidenceHit(),
                evaluation.forbiddenAnswerHit(),
                evaluation.scopeViolation(),
                evalCase.rerankUpliftCandidate(),
                claimSupport.required(),
                claimSupport.claimCount(),
                claimSupport.supportedClaimCount(),
                claimSupport.unsupportedClaimCount(),
                claimSupport.claimSupportHit(),
                claimSupport.forbiddenClaimHit(),
                passed,
                failureReasons
        );
    }
}
