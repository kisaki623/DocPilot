package com.docpilot.backend.ai.service;

import com.docpilot.backend.ai.rag.vector.VectorSearchHit;
import com.docpilot.backend.common.error.ErrorCode;
import com.docpilot.backend.common.exception.BusinessException;
import com.docpilot.backend.document.constant.DocumentStatus;
import com.docpilot.backend.document.entity.Document;
import com.docpilot.backend.document.mapper.DocumentMapper;
import org.springframework.stereotype.Service;

@Service
public class RagScopeGuard {

    private final DocumentMapper documentMapper;

    public RagScopeGuard(DocumentMapper documentMapper) {
        this.documentMapper = documentMapper;
    }

    public Document requireOwnedDocument(Long userId, Long documentId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "userId must not be null");
        }
        if (documentId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "documentId must not be null");
        }
        Document document = documentMapper.selectById(documentId);
        if (document == null) {
            throw new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND);
        }
        if (DocumentStatus.isRemoved(document.getStatus())) {
            throw new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND);
        }
        if (!userId.equals(document.getUserId())) {
            throw new BusinessException(ErrorCode.DOCUMENT_FORBIDDEN);
        }
        return document;
    }

    public void requireHitInScope(Long userId,
                                  Long documentId,
                                  Integer indexVersion,
                                  VectorSearchHit hit) {
        if (hit == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "RAG retrieval returned an invalid hit");
        }
        if (indexVersion == null || indexVersion <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "indexVersion must be positive");
        }
        if (!userId.equals(hit.userId())
                || !documentId.equals(hit.documentId())
                || !indexVersion.equals(hit.indexVersion())) {
            throw new BusinessException(ErrorCode.DOCUMENT_FORBIDDEN, "RAG retrieval hit is outside requested scope");
        }
    }
}
