package com.docpilot.backend.knowledge.vo;

import com.docpilot.backend.knowledge.entity.KnowledgeBase;

import java.util.List;

public class KnowledgeBaseDetailResponse extends KnowledgeBaseResponse {

    private List<KnowledgeBaseDocumentResponse> documents;

    public static KnowledgeBaseDetailResponse from(KnowledgeBase knowledgeBase,
                                                   List<KnowledgeBaseDocumentResponse> documents) {
        KnowledgeBaseDetailResponse response = new KnowledgeBaseDetailResponse();
        response.setId(knowledgeBase.getId());
        response.setUserId(knowledgeBase.getUserId());
        response.setName(knowledgeBase.getName());
        response.setDescription(knowledgeBase.getDescription());
        response.setStatus(knowledgeBase.getStatus());
        response.setCreateTime(knowledgeBase.getCreateTime());
        response.setUpdateTime(knowledgeBase.getUpdateTime());
        response.setDocuments(documents == null ? List.of() : List.copyOf(documents));
        return response;
    }

    public List<KnowledgeBaseDocumentResponse> getDocuments() {
        return documents;
    }

    public void setDocuments(List<KnowledgeBaseDocumentResponse> documents) {
        this.documents = documents;
    }
}
