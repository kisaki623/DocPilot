package com.docpilot.backend.memory.dto;

public class MemorySuggestionResolveRequest {

    private String action;
    private Long activeMemoryId;
    private String mergedContent;
    private Integer priority;

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public Long getActiveMemoryId() {
        return activeMemoryId;
    }

    public void setActiveMemoryId(Long activeMemoryId) {
        this.activeMemoryId = activeMemoryId;
    }

    public String getMergedContent() {
        return mergedContent;
    }

    public void setMergedContent(String mergedContent) {
        this.mergedContent = mergedContent;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }
}
