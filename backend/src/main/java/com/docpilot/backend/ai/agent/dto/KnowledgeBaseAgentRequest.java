package com.docpilot.backend.ai.agent.dto;

public class KnowledgeBaseAgentRequest {

    private String task;
    private Integer topK;
    private Integer indexVersion;
    private String sessionId;
    private Boolean multiQueryEnabled;
    private Integer maxQueryVariants;

    public String getTask() {
        return task;
    }

    public void setTask(String task) {
        this.task = task;
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
