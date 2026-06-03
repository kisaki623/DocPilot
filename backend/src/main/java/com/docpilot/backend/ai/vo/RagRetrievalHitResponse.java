package com.docpilot.backend.ai.vo;

import com.docpilot.backend.ai.rag.RagRetrievalHit;

public class RagRetrievalHitResponse {

    private Integer citationIndex;
    private String vectorId;
    private Double score;
    private Long chunkId;
    private Integer chunkIndex;
    private String content;
    private String contentHash;
    private Integer startOffset;
    private Integer endOffset;
    private Integer tokenCount;
    private String embeddingModel;

    public static RagRetrievalHitResponse from(RagRetrievalHit hit) {
        RagRetrievalHitResponse response = new RagRetrievalHitResponse();
        response.setCitationIndex(hit.citationIndex());
        response.setVectorId(hit.vectorId());
        response.setScore(hit.score());
        response.setChunkId(hit.chunkId());
        response.setChunkIndex(hit.chunkIndex());
        response.setContent(hit.content());
        response.setContentHash(hit.contentHash());
        response.setStartOffset(hit.startOffset());
        response.setEndOffset(hit.endOffset());
        response.setTokenCount(hit.tokenCount());
        response.setEmbeddingModel(hit.embeddingModel());
        return response;
    }

    public Integer getCitationIndex() {
        return citationIndex;
    }

    public void setCitationIndex(Integer citationIndex) {
        this.citationIndex = citationIndex;
    }

    public String getVectorId() {
        return vectorId;
    }

    public void setVectorId(String vectorId) {
        this.vectorId = vectorId;
    }

    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
    }

    public Long getChunkId() {
        return chunkId;
    }

    public void setChunkId(Long chunkId) {
        this.chunkId = chunkId;
    }

    public Integer getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(Integer chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public Integer getStartOffset() {
        return startOffset;
    }

    public void setStartOffset(Integer startOffset) {
        this.startOffset = startOffset;
    }

    public Integer getEndOffset() {
        return endOffset;
    }

    public void setEndOffset(Integer endOffset) {
        this.endOffset = endOffset;
    }

    public Integer getTokenCount() {
        return tokenCount;
    }

    public void setTokenCount(Integer tokenCount) {
        this.tokenCount = tokenCount;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(String embeddingModel) {
        this.embeddingModel = embeddingModel;
    }
}
