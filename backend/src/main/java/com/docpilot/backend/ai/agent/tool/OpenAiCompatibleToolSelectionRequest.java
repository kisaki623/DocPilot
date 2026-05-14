package com.docpilot.backend.ai.agent.tool;

import java.util.List;

public record OpenAiCompatibleToolSelectionRequest(String model,
                                                   List<Message> messages,
                                                   double temperature,
                                                   int maxTokens) {

    public OpenAiCompatibleToolSelectionRequest {
        model = model == null ? "" : model.trim();
        messages = messages == null ? List.of() : List.copyOf(messages);
    }

    public record Message(String role, String content) {

        public Message {
            role = role == null ? "" : role.trim();
            content = content == null ? "" : content;
        }
    }
}
