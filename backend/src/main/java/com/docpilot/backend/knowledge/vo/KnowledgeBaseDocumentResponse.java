package com.docpilot.backend.knowledge.vo;

import com.docpilot.backend.knowledge.entity.KnowledgeBaseDocument;

import java.time.LocalDateTime;

public class KnowledgeBaseDocumentResponse {

    private Long id;
    private Long knowledgeBaseId;
    private Long documentId;
    private String documentTitle;
    private String parseStatus;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public static KnowledgeBaseDocumentResponse from(KnowledgeBaseDocument relation) {
        KnowledgeBaseDocumentResponse response = new KnowledgeBaseDocumentResponse();
        response.setId(relation.getId());
        response.setKnowledgeBaseId(relation.getKnowledgeBaseId());
        response.setDocumentId(relation.getDocumentId());
        response.setStatus(relation.getStatus());
        response.setCreateTime(relation.getCreateTime());
        response.setUpdateTime(relation.getUpdateTime());
        return response;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getKnowledgeBaseId() {
        return knowledgeBaseId;
    }

    public void setKnowledgeBaseId(Long knowledgeBaseId) {
        this.knowledgeBaseId = knowledgeBaseId;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public String getDocumentTitle() {
        return documentTitle;
    }

    public void setDocumentTitle(String documentTitle) {
        this.documentTitle = documentTitle;
    }

    public String getParseStatus() {
        return parseStatus;
    }

    public void setParseStatus(String parseStatus) {
        this.parseStatus = parseStatus;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}
