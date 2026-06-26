package com.docpilot.backend.ai.rag.keyword;

import java.util.Objects;

/**
 * Represents a keyword search hit with BM25 score.
 */
public class KeywordSearchHit {

    private final Long chunkId;
    private final Long documentId;
    private final Long userId;
    private final Integer indexVersion;
    private final Integer chunkIndex;
    private final String content;
    private final String contentHash;
    private final Integer startOffset;
    private final Integer endOffset;
    private final Integer tokenCount;
    private final String embeddingModel;
    private final double score;

    public KeywordSearchHit(Long chunkId, Long documentId, Long userId, Integer indexVersion,
                            Integer chunkIndex, String content, String contentHash,
                            Integer startOffset, Integer endOffset, Integer tokenCount,
                            String embeddingModel, double score) {
        this.chunkId = chunkId;
        this.documentId = documentId;
        this.userId = userId;
        this.indexVersion = indexVersion;
        this.chunkIndex = chunkIndex;
        this.content = content;
        this.contentHash = contentHash;
        this.startOffset = startOffset;
        this.endOffset = endOffset;
        this.tokenCount = tokenCount;
        this.embeddingModel = embeddingModel;
        this.score = score;
    }

    public KeywordSearchHit(Long chunkId, Long documentId, Long userId, Integer chunkIndex, String content, double score) {
        this(chunkId, documentId, userId, 1, chunkIndex, content, "", null, null, null, "", score);
    }

    public Long getChunkId() {
        return chunkId;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public Long getUserId() {
        return userId;
    }

    public Integer getIndexVersion() {
        return indexVersion;
    }

    public Integer getChunkIndex() {
        return chunkIndex;
    }

    public String getContent() {
        return content;
    }

    public String getContentHash() {
        return contentHash;
    }

    public Integer getStartOffset() {
        return startOffset;
    }

    public Integer getEndOffset() {
        return endOffset;
    }

    public Integer getTokenCount() {
        return tokenCount;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public double getScore() {
        return score;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        KeywordSearchHit that = (KeywordSearchHit) o;
        return Objects.equals(chunkId, that.chunkId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(chunkId);
    }

    @Override
    public String toString() {
        return "KeywordSearchHit{" +
                "chunkId=" + chunkId +
                ", documentId=" + documentId +
                ", chunkIndex=" + chunkIndex +
                ", score=" + score +
                '}';
    }
}
