package com.docpilot.backend.conversation.dto;

public class ConversationMessageSendRequest {

    private String content;
    private String groundingPolicy;

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getGroundingPolicy() {
        return groundingPolicy;
    }

    public void setGroundingPolicy(String groundingPolicy) {
        this.groundingPolicy = groundingPolicy;
    }
}
