package com.docpilot.backend.ai.rag;

import java.util.List;
import java.util.Locale;

public class KnowledgeBaseRagPromptBuilder {

    public static final String SYSTEM_PROMPT = """
            You are DocPilot's knowledge-base RAG answer assistant. Answer only from the provided evidence.
            If the evidence is insufficient, say that the answer cannot be confirmed from the knowledge base.
            Use citation markers like [1] and [2] when making claims.
            """;

    public RagPrompt build(String question,
                           List<KnowledgeBaseRagRetrievalHit> hits,
                           int maxContextChars) {
        String resolvedQuestion = question == null ? "" : question.trim();
        if (resolvedQuestion.isBlank()) {
            throw new IllegalArgumentException("question must not be blank");
        }
        if (maxContextChars <= 0) {
            throw new IllegalArgumentException("maxContextChars must be positive");
        }
        List<KnowledgeBaseRagRetrievalHit> resolvedHits = hits == null ? List.of() : hits;
        if (resolvedHits.isEmpty()) {
            return new RagPrompt(SYSTEM_PROMPT, "", noEvidenceUserPrompt(resolvedQuestion), true);
        }

        String evidenceContext = buildEvidenceContext(resolvedHits, maxContextChars);
        String userPrompt = """
                Please answer the user's question using only the numbered knowledge-base evidence below.
                If the evidence does not contain enough information, state that the knowledge-base evidence is insufficient.
                Cite supporting evidence with markers such as [1] or [2].

                User question:
                %s
                """.formatted(resolvedQuestion).trim();
        return new RagPrompt(SYSTEM_PROMPT, evidenceContext, userPrompt, false);
    }

    private String buildEvidenceContext(List<KnowledgeBaseRagRetrievalHit> hits, int maxContextChars) {
        StringBuilder context = new StringBuilder();
        for (KnowledgeBaseRagRetrievalHit hit : hits) {
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
            KnowledgeBaseRagRetrievalHit first = hits.get(0);
            String prefix = evidenceHeader(first);
            int remaining = Math.max(0, maxContextChars - prefix.length() - 1);
            context.append(prefix).append("\n").append(truncate(first.content(), remaining));
        }
        return context.toString().trim();
    }

    private String evidenceBlock(KnowledgeBaseRagRetrievalHit hit) {
        return evidenceHeader(hit) + "\n" + hit.content();
    }

    private String evidenceHeader(KnowledgeBaseRagRetrievalHit hit) {
        return String.format(
                Locale.ROOT,
                "[%d] documentId=%d, title=%s, indexVersion=%d, chunkId=%s, chunkIndex=%d, score=%.4f",
                hit.citationIndex(),
                hit.documentId(),
                hit.documentTitle(),
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
                No evidence was retrieved for the current knowledge-base index.
                Do not answer from prior knowledge.

                User question:
                %s
                """.formatted(question).trim();
    }
}
