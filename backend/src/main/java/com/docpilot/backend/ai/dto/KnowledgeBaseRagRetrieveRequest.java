package com.docpilot.backend.ai.dto;

public class KnowledgeBaseRagRetrieveRequest {

    private String query;
    private Integer topK;
    private Integer indexVersion;
    private Boolean multiQueryEnabled;
    private Integer maxQueryVariants;

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

    public Boolean getMultiQueryEnabled() {
        return multiQueryEnabled;
    }

    public void setMultiQueryEnabled(Boolean multiQueryEnabled) {
        this.multiQueryEnabled = multiQueryEnabled;
    }

    public Integer getMaxQueryVariants() {
        return maxQueryVariants;
    }

    public void setMaxQueryVariants(Integer maxQueryVariants) {
        this.maxQueryVariants = maxQueryVariants;
    }
}
