package com.docpilot.backend.memory.dto;

public class UserMemoryUpdateRequest {

    private String content;
    private Integer priority;

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }
}
