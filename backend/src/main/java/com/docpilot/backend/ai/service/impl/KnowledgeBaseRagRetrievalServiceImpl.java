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

    private final KnowledgeBaseScopeGuard knowledgeBaseScopeGuard;
    private final EmbeddingProvider embeddingProvider;
    private final VectorStoreClient vectorStoreClient;
    private final RagEmbeddingProperties embeddingProperties;
    private final RagQaProperties ragQaProperties;

    public KnowledgeBaseRagRetrievalServiceImpl(KnowledgeBaseScopeGuard knowledgeBaseScopeGuard,
                                                EmbeddingProvider embeddingProvider,
                                                VectorStoreClient vectorStoreClient,
                                                RagEmbeddingProperties embeddingProperties,
                                                RagQaProperties ragQaProperties) {
        this.knowledgeBaseScopeGuard = knowledgeBaseScopeGuard;
        this.embeddingProvider = embeddingProvider;
        this.vectorStoreClient = vectorStoreClient;
        this.embeddingProperties = embeddingProperties == null ? new RagEmbeddingProperties() : embeddingProperties;
        this.ragQaProperties = ragQaProperties == null ? new RagQaProperties() : ragQaProperties;
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
                resolved.topK()
        ));
        Map<Long, KnowledgeBaseDocumentResponse> documentById = documents.stream()
                .collect(Collectors.toMap(
                        KnowledgeBaseDocumentResponse::getDocumentId,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        List<VectorSearchHit> scopedHits = scopedHits(resolved, documentById.keySet(), searchResult.hits());
        List<KnowledgeBaseRagRetrievalHit> hits = toHits(resolved.knowledgeBaseId(), scopedHits, documentById);
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
                embeddingModel
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
                embeddingModel
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

    private record ResolvedQuery(
            Long userId,
            Long knowledgeBaseId,
            String query,
            int topK,
            int indexVersion,
            String embeddingModel
    ) {
    }
}
