package com.docpilot.backend.ai.dto;

public class KnowledgeBaseRagQaRequest {

    private String question;
    private Integer topK;
    private Integer indexVersion;
    private String sessionId;
    private Boolean multiQueryEnabled;
    private Integer maxQueryVariants;

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
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

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
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
