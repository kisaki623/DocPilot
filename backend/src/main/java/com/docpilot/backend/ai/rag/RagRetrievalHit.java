package com.docpilot.backend.ai.rag;

import com.docpilot.backend.ai.rag.vector.VectorSearchHit;

import java.util.Map;

public record RagRetrievalHit(
        int citationIndex,
        String vectorId,
        double score,
        Long userId,
        Long documentId,
        String sourceName,
        Integer indexVersion,
        Long chunkId,
        Integer chunkIndex,
        String content,
        String contentHash,
        Integer startOffset,
        Integer endOffset,
        Integer tokenCount,
        String embeddingModel,
        String sectionPath,
        String structureType,
        Integer pageNumber,
        String sourceLocator,
        String blockType
) {

    private static final int SNIPPET_MAX_LENGTH = 320;

    public RagRetrievalHit(int citationIndex,
                           String vectorId,
                           double score,
                           Long userId,
                           Long documentId,
                           Integer indexVersion,
                           Long chunkId,
                           Integer chunkIndex,
                           String content,
                           String contentHash,
                           Integer startOffset,
                           Integer endOffset,
                           Integer tokenCount,
                           String embeddingModel) {
        this(citationIndex, vectorId, score, userId, documentId, "", indexVersion, chunkId, chunkIndex,
                content, contentHash, startOffset, endOffset, tokenCount, embeddingModel, "", "", null, "", "");
    }

    public RagRetrievalHit(int citationIndex,
                           String vectorId,
                           double score,
                           Long userId,
                           Long documentId,
                           String sourceName,
                           Integer indexVersion,
                           Long chunkId,
                           Integer chunkIndex,
                           String content,
                           String contentHash,
                           Integer startOffset,
                           Integer endOffset,
                           Integer tokenCount,
                           String embeddingModel,
                           String sectionPath,
                           String structureType) {
        this(citationIndex, vectorId, score, userId, documentId, sourceName, indexVersion, chunkId, chunkIndex,
                content, contentHash, startOffset, endOffset, tokenCount, embeddingModel, sectionPath, structureType,
                null, "", "");
    }

    public RagRetrievalHit {
        if (citationIndex <= 0) {
            throw new IllegalArgumentException("citationIndex must be positive");
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
        sourceName = sourceName == null ? "" : sourceName.trim();
        content = content == null ? "" : content.trim();
        contentHash = contentHash == null ? "" : contentHash.trim();
        embeddingModel = embeddingModel == null ? "" : embeddingModel.trim();
        sectionPath = sectionPath == null ? "" : sectionPath.trim();
        structureType = structureType == null ? "" : structureType.trim();
        sourceLocator = sourceLocator == null ? "" : sourceLocator.trim();
        blockType = blockType == null ? "" : blockType.trim();
    }

    public static RagRetrievalHit fromVectorHit(int citationIndex, VectorSearchHit hit) {
        if (hit == null) {
            throw new IllegalArgumentException("hit must not be null");
        }
        Map<String, Object> payload = hit.payload();
        return new RagRetrievalHit(
                citationIndex,
                hit.id(),
                hit.score(),
                hit.userId(),
                hit.documentId(),
                "",
                hit.indexVersion(),
                longValue(payload.get("chunkId")),
                hit.chunkIndex(),
                hit.content(),
                hit.contentHash(),
                intValue(payload.get("startOffset")),
                intValue(payload.get("endOffset")),
                intValue(payload.get("tokenCount")),
                stringValue(payload.get("embeddingModel")),
                stringValue(payload.get("sectionPath")),
                stringValue(payload.get("structureType")),
                intValue(payload.get("pageNumber")),
                stringValue(payload.get("sourceLocator")),
                stringValue(payload.get("blockType"))
        );
    }

    public RagRetrievalHit withSourceName(String resolvedSourceName) {
        return new RagRetrievalHit(
                citationIndex,
                vectorId,
                score,
                userId,
                documentId,
                resolvedSourceName,
                indexVersion,
                chunkId,
                chunkIndex,
                content,
                contentHash,
                startOffset,
                endOffset,
                tokenCount,
                embeddingModel,
                sectionPath,
                structureType,
                pageNumber,
                sourceLocator,
                blockType
        );
    }

    public RagEvidenceCitation toCitation() {
        RagEvidenceQuoteExtractor.EvidenceQuote quote = quote();
        return new RagEvidenceCitation(
                citationIndex,
                documentId,
                sourceName,
                indexVersion,
                chunkId,
                chunkIndex,
                startOffset,
                endOffset,
                contentHash,
                snippet(),
                quote.text(),
                absoluteOffset(quote.startOffset()),
                absoluteOffset(quote.endOffset()),
                sectionPath,
                structureType,
                pageNumber,
                sourceLocator,
                blockType,
                score
        );
    }

    public String snippet() {
        if (content.length() <= SNIPPET_MAX_LENGTH) {
            return content;
        }
        return content.substring(0, SNIPPET_MAX_LENGTH) + "...";
    }

    public String quoteText() {
        return quote().text();
    }

    public Integer quoteStartOffset() {
        return absoluteOffset(quote().startOffset());
    }

    public Integer quoteEndOffset() {
        return absoluteOffset(quote().endOffset());
    }

    private RagEvidenceQuoteExtractor.EvidenceQuote quote() {
        return RagEvidenceQuoteExtractor.extract(content);
    }

    private Integer absoluteOffset(int localOffset) {
        return startOffset == null ? null : startOffset + localOffset;
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
