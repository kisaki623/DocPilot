package com.docpilot.backend.ai.service;

import java.util.function.Consumer;

public interface AiAnswerService {

    String answer(String documentContext, String question);

    default String answerConversation(ConversationAnswerRequest request) {
        throw new UnsupportedOperationException("conversation answer is not implemented");
    }

    void streamAnswer(String documentContext, String question, Consumer<String> chunkConsumer);

    default String provider() {
        return "unknown";
    }

    default String model() {
        return "";
    }
}

