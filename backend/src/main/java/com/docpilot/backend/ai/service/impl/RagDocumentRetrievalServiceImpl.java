package com.docpilot.backend.ai.service.impl;

import com.docpilot.backend.ai.rag.EmbeddingProvider;
import com.docpilot.backend.ai.rag.EmbeddingRequest;
import com.docpilot.backend.ai.rag.EmbeddingResult;
import com.docpilot.backend.ai.rag.RagEmbeddingProperties;
import com.docpilot.backend.ai.rag.RagEvidenceCitation;
import com.docpilot.backend.ai.rag.RagQaProperties;
import com.docpilot.backend.ai.rag.RagRetrievalProperties;
import com.docpilot.backend.ai.rag.RagRetrievalHit;
import com.docpilot.backend.ai.rag.RagRetrievalQuery;
import com.docpilot.backend.ai.rag.RagRetrievalResult;
import com.docpilot.backend.ai.rag.vector.VectorSearchHit;
import com.docpilot.backend.ai.rag.vector.VectorSearchRequest;
import com.docpilot.backend.ai.rag.vector.VectorSearchResult;
import com.docpilot.backend.ai.rag.vector.VectorStoreClient;
import com.docpilot.backend.ai.service.RagDocumentRetrievalService;
import com.docpilot.backend.ai.service.RagScopeGuard;
import com.docpilot.backend.common.error.ErrorCode;
import com.docpilot.backend.common.exception.BusinessException;
import com.docpilot.backend.document.mapper.DocumentMapper;
import com.docpilot.backend.document.entity.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class RagDocumentRetrievalServiceImpl implements RagDocumentRetrievalService {

    public static final int DEFAULT_INDEX_VERSION = 1;
    public static final int MAX_TOP_K = 10;
    private static final Pattern SUPPORT_TOKEN_PATTERN = Pattern.compile("[a-z0-9][a-z0-9_-]{2,}");

    private final DocumentMapper documentMapper;
    private final EmbeddingProvider embeddingProvider;
    private final VectorStoreClient vectorStoreClient;
    private final RagEmbeddingProperties embeddingProperties;
    private final RagQaProperties ragQaProperties;
    private final RagRetrievalProperties retrievalProperties;
    private final RagScopeGuard ragScopeGuard;

    public RagDocumentRetrievalServiceImpl(DocumentMapper documentMapper,
                                           EmbeddingProvider embeddingProvider,
                                           VectorStoreClient vectorStoreClient,
                                           RagEmbeddingProperties embeddingProperties,
                                           RagQaProperties ragQaProperties) {
        this(documentMapper, embeddingProvider, vectorStoreClient, embeddingProperties, ragQaProperties,
                new RagRetrievalProperties(),
                new RagScopeGuard(documentMapper));
    }

    public RagDocumentRetrievalServiceImpl(DocumentMapper documentMapper,
                                           EmbeddingProvider embeddingProvider,
                                           VectorStoreClient vectorStoreClient,
                                           RagEmbeddingProperties embeddingProperties,
                                           RagQaProperties ragQaProperties,
                                           RagScopeGuard ragScopeGuard) {
        this(documentMapper, embeddingProvider, vectorStoreClient, embeddingProperties, ragQaProperties,
                new RagRetrievalProperties(), ragScopeGuard);
    }

    @Autowired
    public RagDocumentRetrievalServiceImpl(DocumentMapper documentMapper,
                                           EmbeddingProvider embeddingProvider,
                                           VectorStoreClient vectorStoreClient,
                                           RagEmbeddingProperties embeddingProperties,
                                           RagQaProperties ragQaProperties,
                                           RagRetrievalProperties retrievalProperties,
                                           RagScopeGuard ragScopeGuard) {
        if (documentMapper == null && ragScopeGuard == null) {
            throw new IllegalArgumentException("documentMapper or ragScopeGuard is required");
        }
        this.documentMapper = documentMapper;
        this.embeddingProvider = embeddingProvider;
        this.vectorStoreClient = vectorStoreClient;
        this.embeddingProperties = embeddingProperties == null ? new RagEmbeddingProperties() : embeddingProperties;
        this.ragQaProperties = ragQaProperties == null ? new RagQaProperties() : ragQaProperties;
        this.retrievalProperties = retrievalProperties == null ? new RagRetrievalProperties() : retrievalProperties;
        this.ragScopeGuard = ragScopeGuard == null ? new RagScopeGuard(documentMapper) : ragScopeGuard;
    }

    @Override
    public RagRetrievalResult retrieve(RagRetrievalQuery query) {
        ResolvedQuery resolved = validateAndResolve(query);
        ragScopeGuard.requireOwnedDocument(resolved.userId(), resolved.documentId());
        EmbeddingResult embedding = embeddingProvider.embed(new EmbeddingRequest(
                resolved.query(),
                resolved.embeddingModel(),
                embeddingMetadata(resolved)
        ));
        VectorSearchResult searchResult = vectorStoreClient.search(new VectorSearchRequest(
                resolved.userId(),
                resolved.documentId(),
                resolved.indexVersion(),
                embedding.vector(),
                resolved.topK()
        ));
        List<VectorSearchHit> scopedHits = scopedHits(resolved, searchResult.hits());
        List<VectorSearchHit> filteredHits = applySimilarityThreshold(resolved, scopedHits);
        List<RagRetrievalHit> hits = toHits(filteredHits, sourceName(resolved.documentId()));
        List<RagEvidenceCitation> citations = hits.stream()
                .map(RagRetrievalHit::toCitation)
                .toList();
        String embeddingModel = !embedding.model().isBlank() ? embedding.model() : resolved.embeddingModel();
        return new RagRetrievalResult(
                resolved.userId(),
                resolved.documentId(),
                resolved.query(),
                resolved.topK(),
                resolved.indexVersion(),
                hits,
                citations,
                hits.isEmpty(),
                searchResult.provider(),
                searchResult.collection(),
                embeddingModel
        );
    }

    private ResolvedQuery validateAndResolve(RagRetrievalQuery query) {
        if (query == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "RAG retrieval request must not be null");
        }
        if (query.userId() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "userId must not be null");
        }
        if (query.documentId() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "documentId must not be null");
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
        String embeddingModel = query.embeddingModel().isBlank() ? embeddingProperties.getModel() : query.embeddingModel();
        return new ResolvedQuery(
                query.userId(),
                query.documentId(),
                query.query(),
                topK,
                indexVersion,
                embeddingModel
        );
    }

    private Map<String, String> embeddingMetadata(ResolvedQuery query) {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("userId", String.valueOf(query.userId()));
        metadata.put("documentId", String.valueOf(query.documentId()));
        metadata.put("indexVersion", String.valueOf(query.indexVersion()));
        metadata.put("usage", "rag_query");
        return metadata;
    }

    private List<VectorSearchHit> scopedHits(ResolvedQuery query, List<VectorSearchHit> hits) {
        if (hits == null || hits.isEmpty()) {
            return List.of();
        }
        List<VectorSearchHit> scoped = new ArrayList<>(hits.size());
        for (VectorSearchHit hit : hits) {
            ragScopeGuard.requireHitInScope(query.userId(), query.documentId(), query.indexVersion(), hit);
            scoped.add(hit);
        }
        return List.copyOf(scoped);
    }

    private List<VectorSearchHit> applySimilarityThreshold(ResolvedQuery query, List<VectorSearchHit> hits) {
        double threshold = retrievalProperties.getMinSimilarityThreshold();
        if (threshold <= 0.0D || hits.isEmpty()) {
            return hits;
        }
        List<VectorSearchHit> filtered = hits.stream()
                .filter(hit -> hit.score() >= threshold)
                .collect(Collectors.toList());
        if (!filtered.isEmpty()) {
            return filtered;
        }
        return markerSupportedFallback(query.query(), hits);
    }

    private List<VectorSearchHit> markerSupportedFallback(String query, List<VectorSearchHit> hits) {
        Set<String> markerTokens = markerTokens(query);
        if (markerTokens.isEmpty()) {
            return List.of();
        }
        return hits.stream()
                .filter(hit -> contentContainsMarker(hit.content(), markerTokens))
                .max(Comparator.comparingDouble(VectorSearchHit::score))
                .map(List::of)
                .orElseGet(List::of);
    }

    private Set<String> markerTokens(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        Set<String> tokens = new LinkedHashSet<>();
        Matcher matcher = SUPPORT_TOKEN_PATTERN.matcher(text.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            String token = matcher.group();
            if (isMarkerToken(token)) {
                tokens.add(token);
            }
        }
        return Set.copyOf(tokens);
    }

    private boolean isMarkerToken(String token) {
        return token != null
                && token.length() >= 6
                && (token.indexOf('-') >= 0 || token.indexOf('_') >= 0);
    }

    private boolean contentContainsMarker(String content, Set<String> markerTokens) {
        if (content == null || content.isBlank() || markerTokens.isEmpty()) {
            return false;
        }
        String normalized = content.toLowerCase(Locale.ROOT);
        return markerTokens.stream().anyMatch(normalized::contains);
    }

    private List<RagRetrievalHit> toHits(List<VectorSearchHit> hits, String sourceName) {
        List<RagRetrievalHit> result = new ArrayList<>();
        for (int i = 0; i < hits.size(); i++) {
            result.add(RagRetrievalHit.fromVectorHit(i + 1, hits.get(i)).withSourceName(sourceName));
        }
        return List.copyOf(result);
    }

    private String sourceName(Long documentId) {
        if (documentId == null || documentMapper == null) {
            return "";
        }
        Document document = documentMapper.selectById(documentId);
        if (document == null || document.getTitle() == null || document.getTitle().isBlank()) {
            return "document-" + documentId;
        }
        return document.getTitle();
    }

    private record ResolvedQuery(
            Long userId,
            Long documentId,
            String query,
            int topK,
            int indexVersion,
            String embeddingModel
    ) {
    }
}
