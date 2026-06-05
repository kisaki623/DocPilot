package com.docpilot.backend.ai.rag;

import com.docpilot.backend.ai.rag.vector.VectorSearchHit;

import java.util.Map;

public record KnowledgeBaseRagRetrievalHit(
        int citationIndex,
        Long knowledgeBaseId,
        String vectorId,
        double score,
        Long userId,
        Long documentId,
        String documentTitle,
        Integer indexVersion,
        Long chunkId,
        Integer chunkIndex,
        String content,
        String contentHash,
        Integer startOffset,
        Integer endOffset,
        Integer tokenCount,
        String embeddingModel
) {

    private static final int SNIPPET_MAX_LENGTH = 320;

    public KnowledgeBaseRagRetrievalHit {
        if (citationIndex <= 0) {
            throw new IllegalArgumentException("citationIndex must be positive");
        }
        if (knowledgeBaseId == null) {
            throw new IllegalArgumentException("knowledgeBaseId must not be null");
        }
        if (vectorId == null || vectorId.isBlank()) {
            throw new IllegalArgumentException("vectorId must not be blank");
        }
        if (!Double.isFinite(score)) {
            throw new IllegalArgumentException("score must be finite");
        }
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        if (documentId == null) {
            throw new IllegalArgumentException("documentId must not be null");
        }
        if (indexVersion == null || indexVersion <= 0) {
            throw new IllegalArgumentException("indexVersion must be positive");
        }
        if (chunkIndex == null || chunkIndex < 0) {
            throw new IllegalArgumentException("chunkIndex must not be negative");
        }
        documentTitle = documentTitle == null ? "" : documentTitle.trim();
        content = content == null ? "" : content.trim();
        contentHash = contentHash == null ? "" : contentHash.trim();
        embeddingModel = embeddingModel == null ? "" : embeddingModel.trim();
    }

    public static KnowledgeBaseRagRetrievalHit fromVectorHit(int citationIndex,
                                                             Long knowledgeBaseId,
                                                             String documentTitle,
                                                             VectorSearchHit hit) {
        Map<String, Object> payload = hit.payload();
        return new KnowledgeBaseRagRetrievalHit(
                citationIndex,
                knowledgeBaseId,
                hit.id(),
                hit.score(),
                hit.userId(),
                hit.documentId(),
                documentTitle,
                hit.indexVersion(),
                longValue(payload.get("chunkId")),
                hit.chunkIndex(),
                hit.content(),
                hit.contentHash(),
                intValue(payload.get("startOffset")),
                intValue(payload.get("endOffset")),
                intValue(payload.get("tokenCount")),
                stringValue(payload.get("embeddingModel"))
        );
    }

    public KnowledgeBaseRagEvidenceCitation toCitation() {
        return new KnowledgeBaseRagEvidenceCitation(
                citationIndex,
                knowledgeBaseId,
                documentId,
                documentTitle,
                indexVersion,
                chunkId,
                chunkIndex,
                startOffset,
                endOffset,
                contentHash,
                snippet(),
                score
        );
    }

    public String snippet() {
        if (content.length() <= SNIPPET_MAX_LENGTH) {
            return content;
        }
        return content.substring(0, SNIPPET_MAX_LENGTH) + "...";
    }

    private static Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static Integer intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
