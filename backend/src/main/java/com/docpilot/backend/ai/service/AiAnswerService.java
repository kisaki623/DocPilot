package com.docpilot.backend.ai.service;

import java.util.function.Consumer;

public interface AiAnswerService {

    String answer(String documentContext, String question);

    void streamAnswer(String documentContext, String question, Consumer<String> chunkConsumer);
}

