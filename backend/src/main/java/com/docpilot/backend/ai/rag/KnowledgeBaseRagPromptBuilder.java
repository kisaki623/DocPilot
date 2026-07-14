package com.docpilot.backend.ai.rag;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public class KnowledgeBaseRagPromptBuilder {

    public static final String SYSTEM_PROMPT = """
            You are DocPilot's knowledge-base RAG answer assistant. Answer only from the provided evidence.
            If the evidence is insufficient, say that the answer cannot be confirmed from the knowledge base.
            Use citation markers like [1] and [2] when making claims.
            """;
    private static final List<String> SUMMARY_INTENT_KEYWORDS = List.of(
            "总结",
            "概括",
            "资料集",
            "知识库",
            "所有文档",
            "全部文档",
            "文档内容",
            "summarize",
            "summary",
            "overview",
            "corpus",
            "knowledge base",
            "all documents"
    );

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
        String userPrompt = isCorrectionIntent(resolvedQuestion)
                ? correctionUserPrompt(resolvedQuestion)
                : isSummaryIntent(resolvedQuestion)
                ? summaryUserPrompt(resolvedQuestion)
                : defaultUserPrompt(resolvedQuestion);
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

    private boolean isSummaryIntent(String question) {
        String normalized = question == null ? "" : question.toLowerCase(Locale.ROOT);
        for (String keyword : SUMMARY_INTENT_KEYWORDS) {
            if (normalized.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private boolean isCorrectionIntent(String question) {
        String normalized = question == null ? "" : question.toLowerCase(Locale.ROOT);
        return normalized.contains("对吗")
                || normalized.contains("正确吗")
                || normalized.contains("是不是")
                || normalized.contains("还是")
                || normalized.contains("只能")
                || normalized.contains("被否决")
                || normalized.contains("废弃")
                || normalized.contains("旧草案")
                || normalized.contains("obsolete draft")
                || normalized.contains("rejected draft")
                || normalized.contains("current rule")
                || containsEnglishWord(normalized, "correct");
    }

    private boolean containsEnglishWord(String normalized, String word) {
        return Pattern.compile("\\b" + Pattern.quote(word) + "\\b").matcher(normalized).find();
    }

    private String defaultUserPrompt(String question) {
        return """
                Please answer the user's question using only the numbered knowledge-base evidence below.
                If the evidence does not contain enough information, state that the knowledge-base evidence is insufficient.
                Cite supporting evidence with markers such as [1] or [2].

                User question:
                %s
                """.formatted(question).trim();
    }

    private String correctionUserPrompt(String question) {
        return """
                Please answer the user's question using only the numbered knowledge-base evidence below.
                If the user asks whether a proposed value, rule, or premise is correct, first say whether it is supported by the evidence, then state the current or effective rule from the evidence.
                Include directly related caps, deadlines, exceptions, or retention limits only when they qualify the rule being corrected.
                If evidence distinguishes current rules from obsolete drafts or rejected plans, make that distinction explicit.
                Cite supporting evidence with markers such as [1] or [2].

                User question:
                %s
                """.formatted(question).trim();
    }

    private String summaryUserPrompt(String question) {
        return """
                The user is asking for an overview of the whole knowledge base or dataset.
                Use only the numbered evidence. First summarize the overall theme, then answer the user's requested summary or synthesis directly.
                If the user requests a specific number of items, provide that many numbered items when the evidence supports them.
                Do not skip any represented title or documentId: include at least one concrete, evidence-backed point from every represented document when synthesizing across documents.
                For risk-control or control-measure questions, extract concrete controls from the evidence, such as approval controls, retention or audit controls, credential or token controls, logging restrictions, and operational mitigations when they are present.
                Then summarize the covered documents by title.
                If evidence from some knowledge-base documents is missing, say which document titles or documentIds are not represented in the evidence instead of inventing their contents.
                Cite supporting evidence with markers such as [1] or [2].

                User question:
                %s
                """.formatted(question).trim();
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
