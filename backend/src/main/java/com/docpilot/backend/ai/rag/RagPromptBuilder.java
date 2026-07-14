package com.docpilot.backend.ai.rag;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public class RagPromptBuilder {

    public static final String SYSTEM_PROMPT = """
            You are DocPilot's RAG answer assistant. Answer only from the provided evidence.
            If the evidence is insufficient, say that the answer cannot be confirmed from the document.
            Use citation markers like [1] and [2] when making claims.
            """;

    public RagPrompt build(String question,
                           List<RagRetrievalHit> hits,
                           Map<String, String> documentMetadata,
                           int maxContextChars) {
        String resolvedQuestion = question == null ? "" : question.trim();
        if (resolvedQuestion.isBlank()) {
            throw new IllegalArgumentException("question must not be blank");
        }
        if (maxContextChars <= 0) {
            throw new IllegalArgumentException("maxContextChars must be positive");
        }
        List<RagRetrievalHit> resolvedHits = hits == null ? List.of() : hits;
        if (resolvedHits.isEmpty()) {
            return new RagPrompt(SYSTEM_PROMPT, "", noEvidenceUserPrompt(resolvedQuestion), true);
        }

        String evidenceContext = buildEvidenceContext(resolvedHits, documentMetadata, maxContextChars);
        String userPrompt = """
                Please answer the user's question using only the numbered evidence below.
                If the evidence does not contain enough information, state that the document evidence is insufficient.
                Cite supporting evidence with markers such as [1] or [2].

                User question:
                %s
                """.formatted(resolvedQuestion).trim();
        return new RagPrompt(SYSTEM_PROMPT, evidenceContext, userPrompt, false);
    }

    private String buildEvidenceContext(List<RagRetrievalHit> hits,
                                        Map<String, String> documentMetadata,
                                        int maxContextChars) {
        StringBuilder context = new StringBuilder();
        appendMetadata(context, documentMetadata);
        for (RagRetrievalHit hit : hits) {
            String block = evidenceBlock(hit);
            int nextLength = context.isEmpty() ? block.length() : context.length() + 2 + block.length();
            if (nextLength > maxContextChars) {
                break;
            }
            if (!context.isEmpty()) {
                context.append("\n\n");
            }
            context.append(block);
        }
        if (context.isEmpty()) {
            RagRetrievalHit first = hits.get(0);
            String prefix = evidenceHeader(first);
            int remaining = Math.max(0, maxContextChars - prefix.length() - 1);
            String content = truncate(first.content(), remaining);
            context.append(prefix).append("\n").append(content);
        }
        return context.toString().trim();
    }

    private void appendMetadata(StringBuilder context, Map<String, String> documentMetadata) {
        if (documentMetadata == null || documentMetadata.isEmpty()) {
            return;
        }
        String title = documentMetadata.get("title");
        if (title != null && !title.isBlank()) {
            context.append("Document title: ").append(title.trim());
        }
    }

    private String evidenceBlock(RagRetrievalHit hit) {
        return evidenceHeader(hit) + "\n" + hit.content();
    }

    private String evidenceHeader(RagRetrievalHit hit) {
        return String.format(
                Locale.ROOT,
                "[%d] documentId=%d, indexVersion=%d, chunkId=%s, chunkIndex=%d, score=%.4f",
                hit.citationIndex(),
                hit.documentId(),
                hit.indexVersion(),
                hit.chunkId() == null ? "" : String.valueOf(hit.chunkId()),
                hit.chunkIndex(),
                hit.score()
        );
    }

    private String truncate(String text, int maxLength) {
        String resolved = text == null ? "" : text.trim();
        if (resolved.length() <= maxLength) {
            return resolved;
        }
        if (maxLength <= 3) {
            return resolved.substring(0, Math.max(0, maxLength));
        }
        return resolved.substring(0, maxLength - 3) + "...";
    }

    private String noEvidenceUserPrompt(String question) {
        return """
                No evidence was retrieved for the current document index.
                Do not answer from prior knowledge.

                User question:
                %s
                """.formatted(question).trim();
    }
}
