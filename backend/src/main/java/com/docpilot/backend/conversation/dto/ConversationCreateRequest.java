package com.docpilot.backend.conversation.dto;

public class ConversationCreateRequest {

    private String title;
    private String contextMode;
    private Long boundKnowledgeBaseId;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContextMode() {
        return contextMode;
    }

    public void setContextMode(String contextMode) {
        this.contextMode = contextMode;
    }

    public Long getBoundKnowledgeBaseId() {
        return boundKnowledgeBaseId;
    }

    public void setBoundKnowledgeBaseId(Long boundKnowledgeBaseId) {
        this.boundKnowledgeBaseId = boundKnowledgeBaseId;
    }
}
