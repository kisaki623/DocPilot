package com.docpilot.backend.ai.service.impl;

import com.docpilot.backend.ai.rag.EmbeddingProvider;
import com.docpilot.backend.ai.rag.EmbeddingRequest;
import com.docpilot.backend.ai.rag.EmbeddingResult;
import com.docpilot.backend.ai.rag.KnowledgeBaseRagEvidenceCitation;
import com.docpilot.backend.ai.rag.KnowledgeBaseRagRetrievalHit;
import com.docpilot.backend.ai.rag.KnowledgeBaseRagRetrievalQuery;
import com.docpilot.backend.ai.rag.KnowledgeBaseRagRetrievalResult;
import com.docpilot.backend.ai.rag.RagEmbeddingProperties;
import com.docpilot.backend.ai.rag.RagQaProperties;
import com.docpilot.backend.ai.rag.RagRetrievalProperties;
import com.docpilot.backend.ai.rag.fusion.FusedSearchHit;
import com.docpilot.backend.ai.rag.fusion.HybridRetrievalService;
import com.docpilot.backend.ai.rag.rerank.RerankProperties;
import com.docpilot.backend.ai.rag.rerank.RerankRequest;
import com.docpilot.backend.ai.rag.rerank.RerankResult;
import com.docpilot.backend.ai.rag.rerank.RerankService;
import com.docpilot.backend.ai.rag.vector.VectorSearchHit;
import com.docpilot.backend.ai.rag.vector.VectorSearchRequest;
import com.docpilot.backend.ai.rag.vector.VectorSearchResult;
import com.docpilot.backend.ai.rag.vector.VectorStoreClient;
import com.docpilot.backend.ai.service.KnowledgeBaseRagRetrievalService;
import com.docpilot.backend.common.error.ErrorCode;
import com.docpilot.backend.common.exception.BusinessException;
import com.docpilot.backend.knowledge.service.KnowledgeBaseScopeGuard;
import com.docpilot.backend.knowledge.vo.KnowledgeBaseDocumentResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class KnowledgeBaseRagRetrievalServiceImpl implements KnowledgeBaseRagRetrievalService {

    public static final int DEFAULT_INDEX_VERSION = 1;
    public static final int MAX_TOP_K = 10;
    private static final int MAX_CANDIDATE_TOP_K = 50;
    private static final int SUMMARY_MAX_HITS_PER_DOCUMENT = 2;
    private static final int DEFAULT_MAX_HITS_PER_DOCUMENT = 3;
    private static final List<String> SUMMARY_INTENT_KEYWORDS = List.of(
            "总结",
            "概括",
            "资料集",
            "知识库",
            "所有文档",
            "全部文档",
            "文档内容",
            "summarize",
            "summary",
            "overview",
            "corpus",
            "knowledge base",
            "all documents"
    );

    private final KnowledgeBaseScopeGuard knowledgeBaseScopeGuard;
    private final EmbeddingProvider embeddingProvider;
    private final VectorStoreClient vectorStoreClient;
    private final HybridRetrievalService hybridRetrievalService;
    private final RagEmbeddingProperties embeddingProperties;
    private final RagQaProperties ragQaProperties;
    private final RagRetrievalProperties retrievalProperties;
    private final RerankService rerankService;
    private final RerankProperties rerankProperties;

    public KnowledgeBaseRagRetrievalServiceImpl(KnowledgeBaseScopeGuard knowledgeBaseScopeGuard,
                                                EmbeddingProvider embeddingProvider,
                                                VectorStoreClient vectorStoreClient,
                                                HybridRetrievalService hybridRetrievalService,
                                                RagEmbeddingProperties embeddingProperties,
                                                RagQaProperties ragQaProperties,
                                                RagRetrievalProperties retrievalProperties,
                                                RerankService rerankService,
                                                RerankProperties rerankProperties) {
        this.knowledgeBaseScopeGuard = knowledgeBaseScopeGuard;
        this.embeddingProvider = embeddingProvider;
        this.vectorStoreClient = vectorStoreClient;
        this.hybridRetrievalService = hybridRetrievalService;
        this.embeddingProperties = embeddingProperties == null ? new RagEmbeddingProperties() : embeddingProperties;
        this.ragQaProperties = ragQaProperties == null ? new RagQaProperties() : ragQaProperties;
        this.retrievalProperties = retrievalProperties == null ? new RagRetrievalProperties() : retrievalProperties;
        this.rerankService = rerankService == null ? KnowledgeBaseRagRetrievalServiceImpl::identityRerank : rerankService;
        this.rerankProperties = rerankProperties == null ? new RerankProperties() : rerankProperties;
    }

    @Override
    public KnowledgeBaseRagRetrievalResult retrieve(KnowledgeBaseRagRetrievalQuery query) {
        ResolvedQuery resolved = validateAndResolve(query);
        List<KnowledgeBaseDocumentResponse> documents = knowledgeBaseScopeGuard.listActiveKnowledgeBaseDocuments(
                resolved.userId(),
                resolved.knowledgeBaseId()
        );
        List<Long> documentIds = documents.stream().map(KnowledgeBaseDocumentResponse::getDocumentId).toList();
        if (documentIds.isEmpty()) {
            return noEvidenceResult(resolved, List.of(), "", "", resolved.embeddingModel());
        }

        EmbeddingResult embedding = embeddingProvider.embed(new EmbeddingRequest(
                resolved.query(),
                resolved.embeddingModel(),
                embeddingMetadata(resolved, documentIds)
        ));
        VectorSearchResult searchResult = vectorStoreClient.search(VectorSearchRequest.forDocuments(
                resolved.userId(),
                documentIds,
                resolved.indexVersion(),
                embedding.vector(),
                candidateTopK(resolved, documentIds.size())
        ));
        Map<Long, KnowledgeBaseDocumentResponse> documentById = documents.stream()
                .collect(Collectors.toMap(
                        KnowledgeBaseDocumentResponse::getDocumentId,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        List<VectorSearchHit> scopedHits = scopedHits(resolved, documentById.keySet(), searchResult.hits());

        // Apply similarity threshold filtering
        List<VectorSearchHit> filteredHits = applySimilarityThreshold(scopedHits);

        // Use hybrid retrieval if enabled, otherwise use vector-only
        String retrievalMode = retrievalProperties.isHybridEnabled() ? "hybrid" : "vector";
        List<VectorSearchHit> candidates;
        if (retrievalProperties.isHybridEnabled()) {
            candidates = hybridRetrieve(resolved, documentIds, filteredHits);
        } else {
            candidates = filteredHits;
        }
        List<VectorSearchHit> finalScopedCandidates = scopedHits(resolved, documentById.keySet(), candidates);
        RerankOutcome rerankOutcome = rerankCandidates(resolved, finalScopedCandidates);
        List<VectorSearchHit> selectedHits = selectDiverseHits(resolved, documentIds, rerankOutcome.hits());

        List<KnowledgeBaseRagRetrievalHit> hits = toHits(resolved.knowledgeBaseId(), selectedHits, documentById);
        List<KnowledgeBaseRagEvidenceCitation> citations = hits.stream()
                .map(KnowledgeBaseRagRetrievalHit::toCitation)
                .toList();
        String embeddingModel = !embedding.model().isBlank() ? embedding.model() : resolved.embeddingModel();
        return new KnowledgeBaseRagRetrievalResult(
                resolved.userId(),
                resolved.knowledgeBaseId(),
                resolved.query(),
                resolved.topK(),
                resolved.indexVersion(),
                documentIds,
                hits,
                citations,
                hits.isEmpty(),
                searchResult.provider(),
                searchResult.collection(),
                embeddingModel,
                documentHitCounts(documentIds, hits),
                retrievalMode,
                rerankOutcome.applied(),
                rerankOutcome.model()
        );
    }

    private ResolvedQuery validateAndResolve(KnowledgeBaseRagRetrievalQuery query) {
        if (query == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "knowledge base RAG retrieval request must not be null");
        }
        if (query.userId() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "userId must not be null");
        }
        if (query.knowledgeBaseId() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "knowledgeBaseId must not be null");
        }
        if (query.query().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "query must not be blank");
        }
        int topK = query.topK() == null ? ragQaProperties.getTopK() : query.topK();
        if (topK <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "topK must be positive");
        }
        topK = Math.min(topK, MAX_TOP_K);
        int indexVersion = query.indexVersion() == null ? DEFAULT_INDEX_VERSION : query.indexVersion();
        if (indexVersion <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "indexVersion must be positive");
        }
        String embeddingModel = query.embeddingModel().isBlank()
                ? embeddingProperties.getModel()
                : query.embeddingModel();
        return new ResolvedQuery(query.userId(), query.knowledgeBaseId(), query.query(), topK,
                indexVersion, embeddingModel);
    }

    private KnowledgeBaseRagRetrievalResult noEvidenceResult(ResolvedQuery query,
                                                             List<Long> documentIds,
                                                             String provider,
                                                             String collection,
                                                             String embeddingModel) {
        return new KnowledgeBaseRagRetrievalResult(
                query.userId(),
                query.knowledgeBaseId(),
                query.query(),
                query.topK(),
                query.indexVersion(),
                documentIds,
                List.of(),
                List.of(),
                true,
                provider,
                collection,
                embeddingModel,
                documentHitCounts(documentIds, List.of()),
                "vector",
                false,
                ""
        );
    }

    private Map<String, String> embeddingMetadata(ResolvedQuery query, List<Long> documentIds) {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("userId", String.valueOf(query.userId()));
        metadata.put("knowledgeBaseId", String.valueOf(query.knowledgeBaseId()));
        metadata.put("documentIds", documentIds.stream().map(String::valueOf).collect(Collectors.joining(",")));
        metadata.put("indexVersion", String.valueOf(query.indexVersion()));
        metadata.put("usage", "knowledge_base_rag_query");
        return metadata;
    }

    private int candidateTopK(ResolvedQuery query, int documentCount) {
        int candidateTopK = Math.max(query.topK() * 4, documentCount * 3);
        return Math.max(query.topK(), Math.min(MAX_CANDIDATE_TOP_K, candidateTopK));
    }

    private List<VectorSearchHit> scopedHits(ResolvedQuery query, Set<Long> allowedDocumentIds, List<VectorSearchHit> hits) {
        if (hits == null || hits.isEmpty()) {
            return List.of();
        }
        List<VectorSearchHit> scoped = new ArrayList<>(hits.size());
        for (VectorSearchHit hit : hits) {
            knowledgeBaseScopeGuard.requireHitInKnowledgeBaseScope(
                    query.userId(),
                    query.knowledgeBaseId(),
                    allowedDocumentIds,
                    query.indexVersion(),
                    hit
            );
            scoped.add(hit);
        }
        return List.copyOf(scoped);
    }

    private List<VectorSearchHit> selectDiverseHits(ResolvedQuery query,
                                                    List<Long> documentIds,
                                                    List<VectorSearchHit> hits) {
        if (hits.isEmpty() || hits.size() <= query.topK()) {
            return hits;
        }
        boolean summaryIntent = isSummaryIntent(query.query());
        int maxHitsPerDocument = summaryIntent ? SUMMARY_MAX_HITS_PER_DOCUMENT : DEFAULT_MAX_HITS_PER_DOCUMENT;
        List<VectorSearchHit> selected = new ArrayList<>(query.topK());
        Set<String> selectedIds = new HashSet<>();
        Map<Long, Integer> counts = new LinkedHashMap<>();
        for (Long documentId : documentIds) {
            counts.put(documentId, 0);
        }

        if (summaryIntent) {
            for (Long documentId : documentIds) {
                VectorSearchHit bestHit = firstHitForDocument(hits, documentId, selectedIds);
                if (bestHit != null) {
                    addSelectedHit(bestHit, selected, selectedIds, counts);
                    if (selected.size() >= query.topK()) {
                        return List.copyOf(selected);
                    }
                }
            }
        }

        for (VectorSearchHit hit : hits) {
            if (selected.size() >= query.topK()) {
                break;
            }
            if (isSelected(hit, selectedIds)) {
                continue;
            }
            int currentCount = counts.getOrDefault(hit.documentId(), 0);
            if (currentCount >= maxHitsPerDocument) {
                continue;
            }
            addSelectedHit(hit, selected, selectedIds, counts);
        }
        return List.copyOf(selected);
    }

    private VectorSearchHit firstHitForDocument(List<VectorSearchHit> hits, Long documentId, Set<String> selectedIds) {
        for (VectorSearchHit hit : hits) {
            if (documentId.equals(hit.documentId()) && !isSelected(hit, selectedIds)) {
                return hit;
            }
        }
        return null;
    }

    private void addSelectedHit(VectorSearchHit hit,
                                List<VectorSearchHit> selected,
                                Set<String> selectedIds,
                                Map<Long, Integer> counts) {
        selected.add(hit);
        selectedIds.add(hit.id());
        counts.merge(hit.documentId(), 1, Integer::sum);
    }

    private boolean isSelected(VectorSearchHit hit, Set<String> selectedIds) {
        return selectedIds.contains(hit.id());
    }

    private boolean isSummaryIntent(String query) {
        String normalized = query == null ? "" : query.toLowerCase();
        for (String keyword : SUMMARY_INTENT_KEYWORDS) {
            if (normalized.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private List<KnowledgeBaseRagRetrievalHit> toHits(Long knowledgeBaseId,
                                                      List<VectorSearchHit> hits,
                                                      Map<Long, KnowledgeBaseDocumentResponse> documentById) {
        List<KnowledgeBaseRagRetrievalHit> result = new ArrayList<>();
        for (int i = 0; i < hits.size(); i++) {
            VectorSearchHit hit = hits.get(i);
            KnowledgeBaseDocumentResponse document = documentById.get(hit.documentId());
            String title = document == null ? "" : document.getDocumentTitle();
            result.add(KnowledgeBaseRagRetrievalHit.fromVectorHit(i + 1, knowledgeBaseId, title, hit));
        }
        return List.copyOf(result);
    }

    private Map<Long, Integer> documentHitCounts(List<Long> documentIds, List<KnowledgeBaseRagRetrievalHit> hits) {
        Map<Long, Integer> counts = new LinkedHashMap<>();
        for (Long documentId : documentIds) {
            counts.put(documentId, 0);
        }
        for (KnowledgeBaseRagRetrievalHit hit : hits) {
            counts.merge(hit.documentId(), 1, Integer::sum);
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(counts));
    }

    /**
     * Apply similarity threshold filtering to vector search hits.
     * Filters out hits with scores below the configured threshold.
     */
    private List<VectorSearchHit> applySimilarityThreshold(List<VectorSearchHit> hits) {
        double threshold = retrievalProperties.getMinSimilarityThreshold();
        if (threshold <= 0.0) {
            return hits;
        }
        return hits.stream()
                .filter(hit -> hit.score() >= threshold)
                .collect(Collectors.toList());
    }

    /**
     * Hybrid retrieval combining vector and keyword search using RRF.
     */
    private List<VectorSearchHit> hybridRetrieve(ResolvedQuery resolved,
                                                  List<Long> documentIds,
                                                  List<VectorSearchHit> vectorHits) {
        // Perform hybrid search with RRF fusion
        List<FusedSearchHit> fusedHits = hybridRetrievalService.hybridSearch(
                resolved.query(),
                resolved.userId(),
                documentIds,
                resolved.indexVersion(),
                vectorHits,
                candidateTopK(resolved, documentIds.size())
        );

        // Convert fused hits back to VectorSearchHit for diversity selection
        List<VectorSearchHit> hybridHits = fusedHits.stream()
                .map(this::fusedToVectorHit)
                .collect(Collectors.toList());
        return List.copyOf(hybridHits);
    }

    /**
     * Convert FusedSearchHit back to VectorSearchHit for downstream processing.
     */
    private VectorSearchHit fusedToVectorHit(FusedSearchHit fused) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("chunkId", fused.chunkId());
        payload.put("fusedScore", fused.fusedScore());
        payload.put("vectorScore", fused.vectorScore());
        payload.put("keywordScore", fused.keywordScore());
        putIfNotNull(payload, "startOffset", fused.startOffset());
        putIfNotNull(payload, "endOffset", fused.endOffset());
        putIfNotNull(payload, "tokenCount", fused.tokenCount());
        if (fused.embeddingModel() != null && !fused.embeddingModel().isBlank()) {
            payload.put("embeddingModel", fused.embeddingModel());
        }

        return new VectorSearchHit(
                fused.vectorId() != null ? fused.vectorId() : "fused_" + fused.chunkId(),
                fused.fusedScore(), // Use fused score as the primary score
                fused.userId(),
                fused.documentId(),
                fused.indexVersion(),
                fused.chunkIndex(),
                fused.content(),
                fused.contentHash() == null ? "" : fused.contentHash(),
                payload
        );
    }

    private RerankOutcome rerankCandidates(ResolvedQuery resolved, List<VectorSearchHit> candidates) {
        if (candidates.isEmpty() || !rerankProperties.isEnabled()) {
            return new RerankOutcome(candidates, false, "");
        }
        try {
            RerankResult result = rerankService.rerank(new RerankRequest(
                    resolved.query(),
                    candidates.stream().map(VectorSearchHit::content).toList(),
                    candidates.size()
            ));
            if (result.hits().isEmpty() || "identity".equalsIgnoreCase(result.model())) {
                return new RerankOutcome(candidates, false, result.model());
            }
            List<VectorSearchHit> reranked = applyRerankResult(candidates, result);
            if (reranked.isEmpty()) {
                return new RerankOutcome(candidates, false, result.model());
            }
            return new RerankOutcome(reranked, true, result.model());
        } catch (RuntimeException ex) {
            return new RerankOutcome(candidates, false, "fallback");
        }
    }

    private List<VectorSearchHit> applyRerankResult(List<VectorSearchHit> candidates, RerankResult result) {
        List<VectorSearchHit> reranked = new ArrayList<>(candidates.size());
        Set<Integer> selectedIndexes = new HashSet<>();
        for (RerankResult.RerankHit rerankHit : result.hits()) {
            int index = rerankHit.index();
            if (index < 0 || index >= candidates.size() || !selectedIndexes.add(index)) {
                continue;
            }
            reranked.add(withScoreAndPayload(candidates.get(index), rerankHit.relevanceScore(), Map.of(
                    "rerankScore", rerankHit.relevanceScore(),
                    "rerankModel", result.model()
            )));
        }
        for (int i = 0; i < candidates.size(); i++) {
            if (!selectedIndexes.contains(i)) {
                reranked.add(candidates.get(i));
            }
        }
        return List.copyOf(reranked);
    }

    private VectorSearchHit withScoreAndPayload(VectorSearchHit hit, double score, Map<String, Object> extraPayload) {
        Map<String, Object> payload = new LinkedHashMap<>(hit.payload());
        payload.putAll(extraPayload);
        return new VectorSearchHit(
                hit.id(),
                score,
                hit.userId(),
                hit.documentId(),
                hit.indexVersion(),
                hit.chunkIndex(),
                hit.content(),
                hit.contentHash(),
                payload
        );
    }

    private static RerankResult identityRerank(RerankRequest request) {
        List<RerankResult.RerankHit> hits = new ArrayList<>();
        int topK = Math.min(request.topK(), request.documents().size());
        for (int i = 0; i < topK; i++) {
            hits.add(new RerankResult.RerankHit(i, 1.0D - (i * 0.01D)));
        }
        return new RerankResult(hits, "identity");
    }

    private void putIfNotNull(Map<String, Object> payload, String key, Object value) {
        if (value != null) {
            payload.put(key, value);
        }
    }

    private record ResolvedQuery(
            Long userId,
            Long knowledgeBaseId,
            String query,
            int topK,
            int indexVersion,
            String embeddingModel
    ) {
    }

    private record RerankOutcome(
            List<VectorSearchHit> hits,
            boolean applied,
            String model
    ) {
        private RerankOutcome {
            hits = hits == null ? List.of() : List.copyOf(hits);
            model = model == null ? "" : model.trim();
        }
    }
}
