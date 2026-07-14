package com.docpilot.backend.document.vo;

public class DocumentDeleteResponse {

    private Long documentId;
    private String status;
    private Integer removedKnowledgeBaseRelationCount;

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getRemovedKnowledgeBaseRelationCount() {
        return removedKnowledgeBaseRelationCount;
    }

    public void setRemovedKnowledgeBaseRelationCount(Integer removedKnowledgeBaseRelationCount) {
        this.removedKnowledgeBaseRelationCount = removedKnowledgeBaseRelationCount;
    }
}
