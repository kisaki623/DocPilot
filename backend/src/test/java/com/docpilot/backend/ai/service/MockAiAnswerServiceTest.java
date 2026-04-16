package com.docpilot.backend.ai.service;

import com.docpilot.backend.ai.service.impl.MockAiAnswerService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MockAiAnswerServiceTest {

    @Test
    void shouldStreamAnswerInChunksAndMatchFullAnswer() {
        MockAiAnswerService mockAiAnswerService = new MockAiAnswerService();

        String context = "A".repeat(260);
        String question = "What is this?";

        String fullAnswer = mockAiAnswerService.answer(context, question);

        List<String> chunks = new ArrayList<>();
        mockAiAnswerService.streamAnswer(context, question, chunks::add);

        String streamed = String.join("", chunks);
        assertEquals(fullAnswer, streamed);
        assertTrue(chunks.size() > 1);
    }
}

