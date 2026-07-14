package com.docpilot.backend.memory.dto;

public class MemorySuggestionExtractRequest {

    private Long conversationId;

    private Integer limit;

    public Long getConversationId() {
        return conversationId;
    }

    public void setConversationId(Long conversationId) {
        this.conversationId = conversationId;
    }

    public Integer getLimit() {
        return limit;
    }

    public void setLimit(Integer limit) {
        this.limit = limit;
    }
}
