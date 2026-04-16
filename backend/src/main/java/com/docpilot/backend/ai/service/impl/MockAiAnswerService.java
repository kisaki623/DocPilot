package com.docpilot.backend.ai.service.impl;

import com.docpilot.backend.ai.service.AiAnswerService;
import com.docpilot.backend.common.util.ValidationUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

@Service
@ConditionalOnProperty(prefix = "app.ai", name = "mode", havingValue = "mock", matchIfMissing = true)
public class MockAiAnswerService implements AiAnswerService {

    private static final int ANSWER_CONTEXT_PREVIEW_LENGTH = 240;
    private static final int STREAM_CHUNK_SIZE = 32;

    @Value("${app.ai.mock.stream-chunk-delay-ms:30}")
    private long streamChunkDelayMs;

    @Override
    public String answer(String documentContext, String question) {
        ValidationUtils.requireNonBlank(documentContext, "documentContext");
        ValidationUtils.requireNonBlank(question, "question");

        String preview = documentContext;
        if (preview.length() > ANSWER_CONTEXT_PREVIEW_LENGTH) {
            preview = preview.substring(0, ANSWER_CONTEXT_PREVIEW_LENGTH);
        }

        return "[mock-answer] question: " + question + " | source: current document only | context preview: " + preview;
    }

    @Override
    public void streamAnswer(String documentContext, String question, Consumer<String> chunkConsumer) {
        ValidationUtils.requireNonNull(chunkConsumer, "chunkConsumer");

        String fullAnswer = answer(documentContext, question);
        for (int i = 0; i < fullAnswer.length(); i += STREAM_CHUNK_SIZE) {
            int end = Math.min(i + STREAM_CHUNK_SIZE, fullAnswer.length());
            chunkConsumer.accept(fullAnswer.substring(i, end));

            // Keep mock mode observable in browser as true incremental chunks.
            if (streamChunkDelayMs > 0 && end < fullAnswer.length()) {
                try {
                    Thread.sleep(streamChunkDelayMs);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }
}
