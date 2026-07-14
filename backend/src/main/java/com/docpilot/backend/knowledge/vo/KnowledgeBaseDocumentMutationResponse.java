package com.docpilot.backend.knowledge.vo;

import java.util.List;

public class KnowledgeBaseDocumentMutationResponse {

    private Long knowledgeBaseId;
    private List<Long> documentIds;
    private Integer activeDocumentCount;

    public KnowledgeBaseDocumentMutationResponse() {
    }

    public KnowledgeBaseDocumentMutationResponse(Long knowledgeBaseId,
                                                 List<Long> documentIds,
                                                 Integer activeDocumentCount) {
        this.knowledgeBaseId = knowledgeBaseId;
        this.documentIds = documentIds == null ? List.of() : List.copyOf(documentIds);
        this.activeDocumentCount = activeDocumentCount;
    }

    public Long getKnowledgeBaseId() {
        return knowledgeBaseId;
    }

    public void setKnowledgeBaseId(Long knowledgeBaseId) {
        this.knowledgeBaseId = knowledgeBaseId;
    }

    public List<Long> getDocumentIds() {
        return documentIds;
    }

    public void setDocumentIds(List<Long> documentIds) {
        this.documentIds = documentIds;
    }

    public Integer getActiveDocumentCount() {
        return activeDocumentCount;
    }

    public void setActiveDocumentCount(Integer activeDocumentCount) {
        this.activeDocumentCount = activeDocumentCount;
    }
}
