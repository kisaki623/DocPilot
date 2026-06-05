package com.docpilot.backend.knowledge.service;

import com.docpilot.backend.ai.rag.vector.VectorSearchHit;
import com.docpilot.backend.common.error.ErrorCode;
import com.docpilot.backend.common.exception.BusinessException;
import com.docpilot.backend.document.entity.Document;
import com.docpilot.backend.document.mapper.DocumentMapper;
import com.docpilot.backend.knowledge.constant.KnowledgeBaseStatus;
import com.docpilot.backend.knowledge.entity.KnowledgeBase;
import com.docpilot.backend.knowledge.mapper.KnowledgeBaseDocumentMapper;
import com.docpilot.backend.knowledge.mapper.KnowledgeBaseMapper;
import com.docpilot.backend.knowledge.vo.KnowledgeBaseDocumentResponse;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class KnowledgeBaseScopeGuard {

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeBaseDocumentMapper knowledgeBaseDocumentMapper;
    private final DocumentMapper documentMapper;

    public KnowledgeBaseScopeGuard(KnowledgeBaseMapper knowledgeBaseMapper,
                                   KnowledgeBaseDocumentMapper knowledgeBaseDocumentMapper,
                                   DocumentMapper documentMapper) {
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.knowledgeBaseDocumentMapper = knowledgeBaseDocumentMapper;
        this.documentMapper = documentMapper;
    }

    public KnowledgeBase requireOwnedKnowledgeBase(Long userId, Long knowledgeBaseId) {
        requireUserId(userId);
        if (knowledgeBaseId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "knowledgeBaseId must not be null");
        }
        KnowledgeBase knowledgeBase = knowledgeBaseMapper.selectById(knowledgeBaseId);
        if (knowledgeBase == null) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_BASE_NOT_FOUND);
        }
        if (!KnowledgeBaseStatus.ACTIVE.equals(knowledgeBase.getStatus())) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_BASE_NOT_FOUND);
        }
        if (!userId.equals(knowledgeBase.getUserId())) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_BASE_FORBIDDEN);
        }
        return knowledgeBase;
    }

    public Document requireOwnedDocument(Long userId, Long documentId) {
        requireUserId(userId);
        if (documentId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "documentId must not be null");
        }
        Document document = documentMapper.selectById(documentId);
        if (document == null) {
            throw new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND);
        }
        if (!userId.equals(document.getUserId())) {
            throw new BusinessException(ErrorCode.DOCUMENT_FORBIDDEN);
        }
        return document;
    }

    public List<Document> requireOwnedDocuments(Long userId, List<Long> documentIds) {
        requireUserId(userId);
        List<Long> resolvedIds = normalizeDocumentIds(documentIds);
        if (resolvedIds.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "documentIds must not be empty");
        }
        return resolvedIds.stream()
                .map(documentId -> requireOwnedDocument(userId, documentId))
                .toList();
    }

    public List<KnowledgeBaseDocumentResponse> listActiveKnowledgeBaseDocuments(Long userId,
                                                                                Long knowledgeBaseId) {
        requireOwnedKnowledgeBase(userId, knowledgeBaseId);
        return knowledgeBaseDocumentMapper.selectActiveDocumentResponses(userId, knowledgeBaseId);
    }

    public void requireHitInKnowledgeBaseScope(Long userId,
                                               Long knowledgeBaseId,
                                               Set<Long> allowedDocumentIds,
                                               Integer indexVersion,
                                               VectorSearchHit hit) {
        requireUserId(userId);
        if (knowledgeBaseId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "knowledgeBaseId must not be null");
        }
        if (indexVersion == null || indexVersion <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "indexVersion must be positive");
        }
        if (hit == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "RAG retrieval returned an invalid hit");
        }
        Set<Long> allowed = allowedDocumentIds == null ? Set.of() : new HashSet<>(allowedDocumentIds);
        if (!userId.equals(hit.userId())
                || !allowed.contains(hit.documentId())
                || !indexVersion.equals(hit.indexVersion())) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_BASE_FORBIDDEN,
                    "RAG retrieval hit is outside requested knowledge base scope");
        }
    }

    private List<Long> normalizeDocumentIds(List<Long> documentIds) {
        if (documentIds == null) {
            return List.of();
        }
        return documentIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
    }

    private void requireUserId(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "userId must not be null");
        }
    }
}
