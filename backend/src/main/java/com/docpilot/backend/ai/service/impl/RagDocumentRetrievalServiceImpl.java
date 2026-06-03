package com.docpilot.backend.ai.service.impl;

import com.docpilot.backend.ai.rag.EmbeddingProvider;
import com.docpilot.backend.ai.rag.EmbeddingRequest;
import com.docpilot.backend.ai.rag.EmbeddingResult;
import com.docpilot.backend.ai.rag.RagEmbeddingProperties;
import com.docpilot.backend.ai.rag.RagEvidenceCitation;
import com.docpilot.backend.ai.rag.RagQaProperties;
import com.docpilot.backend.ai.rag.RagRetrievalHit;
import com.docpilot.backend.ai.rag.RagRetrievalQuery;
import com.docpilot.backend.ai.rag.RagRetrievalResult;
import com.docpilot.backend.ai.rag.vector.VectorSearchHit;
import com.docpilot.backend.ai.rag.vector.VectorSearchRequest;
import com.docpilot.backend.ai.rag.vector.VectorSearchResult;
import com.docpilot.backend.ai.rag.vector.VectorStoreClient;
import com.docpilot.backend.ai.service.RagDocumentRetrievalService;
import com.docpilot.backend.common.error.ErrorCode;
import com.docpilot.backend.common.exception.BusinessException;
import com.docpilot.backend.document.entity.Document;
import com.docpilot.backend.document.mapper.DocumentMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RagDocumentRetrievalServiceImpl implements RagDocumentRetrievalService {

    public static final int DEFAULT_INDEX_VERSION = 1;
    public static final int MAX_TOP_K = 10;

    private final DocumentMapper documentMapper;
    private final EmbeddingProvider embeddingProvider;
    private final VectorStoreClient vectorStoreClient;
    private final RagEmbeddingProperties embeddingProperties;
    private final RagQaProperties ragQaProperties;

    public RagDocumentRetrievalServiceImpl(DocumentMapper documentMapper,
                                           EmbeddingProvider embeddingProvider,
                                           VectorStoreClient vectorStoreClient,
                                           RagEmbeddingProperties embeddingProperties,
                                           RagQaProperties ragQaProperties) {
        this.documentMapper = documentMapper;
        this.embeddingProvider = embeddingProvider;
        this.vectorStoreClient = vectorStoreClient;
        this.embeddingProperties = embeddingProperties == null ? new RagEmbeddingProperties() : embeddingProperties;
        this.ragQaProperties = ragQaProperties == null ? new RagQaProperties() : ragQaProperties;
    }

    @Override
    public RagRetrievalResult retrieve(RagRetrievalQuery query) {
        ResolvedQuery resolved = validateAndResolve(query);
        ensureOwnedDocument(resolved.userId(), resolved.documentId());
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
        List<RagRetrievalHit> hits = toHits(searchResult.hits());
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

    private void ensureOwnedDocument(Long userId, Long documentId) {
        Document document = documentMapper.selectById(documentId);
        if (document == null) {
            throw new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND);
        }
        if (!userId.equals(document.getUserId())) {
            throw new BusinessException(ErrorCode.DOCUMENT_FORBIDDEN);
        }
    }

    private Map<String, String> embeddingMetadata(ResolvedQuery query) {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("userId", String.valueOf(query.userId()));
        metadata.put("documentId", String.valueOf(query.documentId()));
        metadata.put("indexVersion", String.valueOf(query.indexVersion()));
        metadata.put("usage", "rag_query");
        return metadata;
    }

    private List<RagRetrievalHit> toHits(List<VectorSearchHit> hits) {
        List<RagRetrievalHit> result = new ArrayList<>();
        for (int i = 0; i < hits.size(); i++) {
            result.add(RagRetrievalHit.fromVectorHit(i + 1, hits.get(i)));
        }
        return List.copyOf(result);
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
