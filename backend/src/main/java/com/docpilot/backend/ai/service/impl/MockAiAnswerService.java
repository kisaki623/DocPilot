package com.docpilot.backend.ai.service.impl;

import com.docpilot.backend.ai.service.AiAnswerService;
import com.docpilot.backend.common.util.ValidationUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

@Service
@ConditionalOnProperty(prefix = "app.ai", name = "mode", havingValue = "mock", matchIfMissing = true)
public class MockAiAnswerService implements AiAnswerService {

    private static final int ANSWER_CONTEXT_PREVIEW_LENGTH = 1200;
    private static final int MAX_SELECTED_LINES = 3;
    private static final int STREAM_CHUNK_SIZE = 32;

    @Value("${app.ai.mock.stream-chunk-delay-ms:30}")
    private long streamChunkDelayMs;

    @Override
    public String answer(String documentContext, String question) {
        ValidationUtils.requireNonBlank(documentContext, "documentContext");
        ValidationUtils.requireNonBlank(question, "question");

        String normalizedContext = documentContext.replace("\r", "").trim();
        List<String> selectedLines = selectRelevantLines(normalizedContext, question);
        String preview = String.join("\n", selectedLines);
        if (preview.isBlank()) {
            preview = normalizedContext;
        }
        if (preview.length() > ANSWER_CONTEXT_PREVIEW_LENGTH) {
            preview = preview.substring(0, ANSWER_CONTEXT_PREVIEW_LENGTH) + "...";
        }

        return "[mock-answer] source=document-only\nquestion: " + question + "\nanswer:\n" + preview;
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

    @Override
    public String provider() {
        return "mock";
    }

    @Override
    public String model() {
        return "mock";
    }

    private List<String> selectRelevantLines(String context, String question) {
        String normalizedQuestion = question.toLowerCase(Locale.ROOT);
        String[] terms = normalizedQuestion.split("[^\\p{L}\\p{N}]+");

        String[] rawLines = context.split("\n");
        List<ScoredLine> scored = new ArrayList<>();
        for (String rawLine : rawLines) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }
            String lowerLine = line.toLowerCase(Locale.ROOT);
            int score = 0;
            for (String term : terms) {
                if (term.length() < 2) {
                    continue;
                }
                if (lowerLine.contains(term)) {
                    score += Math.min(8, term.length());
                }
            }
            if (score > 0) {
                scored.add(new ScoredLine(line, score));
            }
        }

        scored.sort(Comparator.comparingInt(ScoredLine::score).reversed());
        List<String> result = new ArrayList<>();
        for (int i = 0; i < scored.size() && i < MAX_SELECTED_LINES; i++) {
            result.add(scored.get(i).line());
        }

        if (!result.isEmpty()) {
            return result;
        }

        // Fall back to first lines when no term matched.
        for (String rawLine : rawLines) {
            String line = rawLine.trim();
            if (!line.isEmpty()) {
                result.add(line);
                if (result.size() >= MAX_SELECTED_LINES) {
                    break;
                }
            }
        }
        return result;
    }

    private record ScoredLine(String line, int score) {
    }
}
