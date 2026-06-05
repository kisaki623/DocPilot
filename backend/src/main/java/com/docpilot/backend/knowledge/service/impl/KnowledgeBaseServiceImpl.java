package com.docpilot.backend.knowledge.service.impl;

import com.docpilot.backend.common.error.ErrorCode;
import com.docpilot.backend.common.exception.BusinessException;
import com.docpilot.backend.common.util.ValidationUtils;
import com.docpilot.backend.document.entity.Document;
import com.docpilot.backend.knowledge.constant.KnowledgeBaseStatus;
import com.docpilot.backend.knowledge.entity.KnowledgeBase;
import com.docpilot.backend.knowledge.entity.KnowledgeBaseDocument;
import com.docpilot.backend.knowledge.mapper.KnowledgeBaseDocumentMapper;
import com.docpilot.backend.knowledge.mapper.KnowledgeBaseMapper;
import com.docpilot.backend.knowledge.service.KnowledgeBaseScopeGuard;
import com.docpilot.backend.knowledge.service.KnowledgeBaseService;
import com.docpilot.backend.knowledge.vo.KnowledgeBaseDetailResponse;
import com.docpilot.backend.knowledge.vo.KnowledgeBaseDocumentMutationResponse;
import com.docpilot.backend.knowledge.vo.KnowledgeBaseDocumentResponse;
import com.docpilot.backend.knowledge.vo.KnowledgeBaseResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {

    private static final int NAME_MAX_LENGTH = 128;
    private static final int DESCRIPTION_MAX_LENGTH = 512;

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeBaseDocumentMapper knowledgeBaseDocumentMapper;
    private final KnowledgeBaseScopeGuard knowledgeBaseScopeGuard;

    public KnowledgeBaseServiceImpl(KnowledgeBaseMapper knowledgeBaseMapper,
                                    KnowledgeBaseDocumentMapper knowledgeBaseDocumentMapper,
                                    KnowledgeBaseScopeGuard knowledgeBaseScopeGuard) {
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.knowledgeBaseDocumentMapper = knowledgeBaseDocumentMapper;
        this.knowledgeBaseScopeGuard = knowledgeBaseScopeGuard;
    }

    @Override
    public KnowledgeBaseResponse create(Long userId, String name, String description) {
        ValidationUtils.requireNonNull(userId, "userId");
        String resolvedName = resolveName(name);
        String resolvedDescription = resolveDescription(description);

        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setUserId(userId);
        knowledgeBase.setName(resolvedName);
        knowledgeBase.setDescription(resolvedDescription);
        knowledgeBase.setStatus(KnowledgeBaseStatus.ACTIVE);
        try {
            knowledgeBaseMapper.insert(knowledgeBase);
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "knowledge base create failed");
        }
        return KnowledgeBaseResponse.from(knowledgeBase);
    }

    @Override
    public List<KnowledgeBaseResponse> listByUser(Long userId) {
        ValidationUtils.requireNonNull(userId, "userId");
        return knowledgeBaseMapper.selectActiveByUserId(userId).stream()
                .map(KnowledgeBaseResponse::from)
                .toList();
    }

    @Override
    public KnowledgeBaseDetailResponse getDetail(Long userId, Long knowledgeBaseId) {
        KnowledgeBase knowledgeBase = knowledgeBaseScopeGuard.requireOwnedKnowledgeBase(userId, knowledgeBaseId);
        List<KnowledgeBaseDocumentResponse> documents =
                knowledgeBaseDocumentMapper.selectActiveDocumentResponses(userId, knowledgeBaseId);
        return KnowledgeBaseDetailResponse.from(knowledgeBase, documents);
    }

    @Override
    public KnowledgeBaseDocumentMutationResponse addDocuments(Long userId,
                                                              Long knowledgeBaseId,
                                                              List<Long> documentIds) {
        knowledgeBaseScopeGuard.requireOwnedKnowledgeBase(userId, knowledgeBaseId);
        List<Document> documents = knowledgeBaseScopeGuard.requireOwnedDocuments(userId, documentIds);
        List<Long> resolvedDocumentIds = documents.stream().map(Document::getId).toList();
        for (Document document : documents) {
            upsertActiveRelation(userId, knowledgeBaseId, document.getId());
        }
        return mutationResponse(userId, knowledgeBaseId, resolvedDocumentIds);
    }

    @Override
    public KnowledgeBaseDocumentMutationResponse removeDocument(Long userId,
                                                                Long knowledgeBaseId,
                                                                Long documentId) {
        knowledgeBaseScopeGuard.requireOwnedKnowledgeBase(userId, knowledgeBaseId);
        knowledgeBaseScopeGuard.requireOwnedDocument(userId, documentId);
        KnowledgeBaseDocument relation =
                knowledgeBaseDocumentMapper.selectByKnowledgeBaseIdAndDocumentId(knowledgeBaseId, documentId);
        if (relation != null && KnowledgeBaseStatus.ACTIVE.equals(relation.getStatus())) {
            knowledgeBaseDocumentMapper.updateStatus(knowledgeBaseId, documentId, KnowledgeBaseStatus.REMOVED);
        }
        return mutationResponse(userId, knowledgeBaseId, List.of(documentId));
    }

    private void upsertActiveRelation(Long userId, Long knowledgeBaseId, Long documentId) {
        KnowledgeBaseDocument existing =
                knowledgeBaseDocumentMapper.selectByKnowledgeBaseIdAndDocumentId(knowledgeBaseId, documentId);
        if (existing == null) {
            KnowledgeBaseDocument relation = new KnowledgeBaseDocument();
            relation.setUserId(userId);
            relation.setKnowledgeBaseId(knowledgeBaseId);
            relation.setDocumentId(documentId);
            relation.setStatus(KnowledgeBaseStatus.ACTIVE);
            knowledgeBaseDocumentMapper.insert(relation);
            return;
        }
        if (!KnowledgeBaseStatus.ACTIVE.equals(existing.getStatus())) {
            knowledgeBaseDocumentMapper.updateStatus(knowledgeBaseId, documentId, KnowledgeBaseStatus.ACTIVE);
        }
    }

    private KnowledgeBaseDocumentMutationResponse mutationResponse(Long userId,
                                                                   Long knowledgeBaseId,
                                                                   List<Long> documentIds) {
        Integer count = knowledgeBaseDocumentMapper.countActiveDocuments(userId, knowledgeBaseId);
        return new KnowledgeBaseDocumentMutationResponse(
                knowledgeBaseId,
                documentIds,
                count == null ? 0 : count
        );
    }

    private String resolveName(String name) {
        ValidationUtils.requireNonBlank(name, "name");
        String resolved = name.trim();
        if (resolved.length() > NAME_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "name is too long");
        }
        return resolved;
    }

    private String resolveDescription(String description) {
        if (description == null || description.isBlank()) {
            return "";
        }
        String resolved = description.trim();
        if (resolved.length() > DESCRIPTION_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "description is too long");
        }
        return resolved;
    }
}
