package com.docpilot.backend.ai.dto;

public class RagRetrieveRequest {

    private Long documentId;
    private String query;
    private Integer topK;
    private Integer indexVersion;

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public Integer getTopK() {
        return topK;
    }

    public void setTopK(Integer topK) {
        this.topK = topK;
    }

    public Integer getIndexVersion() {
        return indexVersion;
    }

    public void setIndexVersion(Integer indexVersion) {
        this.indexVersion = indexVersion;
    }
}
