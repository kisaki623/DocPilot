package com.docpilot.backend.knowledge.service;

import com.docpilot.backend.knowledge.vo.KnowledgeBaseDetailResponse;
import com.docpilot.backend.knowledge.vo.KnowledgeBaseDocumentMutationResponse;
import com.docpilot.backend.knowledge.vo.KnowledgeBaseResponse;

import java.util.List;

public interface KnowledgeBaseService {

    KnowledgeBaseResponse create(Long userId, String name, String description);

    List<KnowledgeBaseResponse> listByUser(Long userId);

    KnowledgeBaseDetailResponse getDetail(Long userId, Long knowledgeBaseId);

    KnowledgeBaseDocumentMutationResponse addDocuments(Long userId, Long knowledgeBaseId, List<Long> documentIds);

    KnowledgeBaseDocumentMutationResponse removeDocument(Long userId, Long knowledgeBaseId, Long documentId);
}
