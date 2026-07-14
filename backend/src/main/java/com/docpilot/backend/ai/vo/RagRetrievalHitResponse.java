package com.docpilot.backend.ai.vo;

import com.docpilot.backend.ai.rag.RagRetrievalHit;

public class RagRetrievalHitResponse {

    private Integer citationIndex;
    private String vectorId;
    private Double score;
    private String sourceName;
    private Long chunkId;
    private Integer chunkIndex;
    private String content;
    private String contentHash;
    private Integer startOffset;
    private Integer endOffset;
    private String quoteText;
    private Integer quoteStartOffset;
    private Integer quoteEndOffset;
    private Integer tokenCount;
    private String embeddingModel;
    private String sectionPath;
    private String structureType;
    private Integer pageNumber;
    private String sourceLocator;
    private String blockType;

    public static RagRetrievalHitResponse from(RagRetrievalHit hit) {
        RagRetrievalHitResponse response = new RagRetrievalHitResponse();
        response.setCitationIndex(hit.citationIndex());
        response.setVectorId(hit.vectorId());
        response.setScore(hit.score());
        response.setSourceName(hit.sourceName());
        response.setChunkId(hit.chunkId());
        response.setChunkIndex(hit.chunkIndex());
        response.setContent(hit.content());
        response.setContentHash(hit.contentHash());
        response.setStartOffset(hit.startOffset());
        response.setEndOffset(hit.endOffset());
        response.setQuoteText(hit.quoteText());
        response.setQuoteStartOffset(hit.quoteStartOffset());
        response.setQuoteEndOffset(hit.quoteEndOffset());
        response.setTokenCount(hit.tokenCount());
        response.setEmbeddingModel(hit.embeddingModel());
        response.setSectionPath(hit.sectionPath());
        response.setStructureType(hit.structureType());
        response.setPageNumber(hit.pageNumber());
        response.setSourceLocator(hit.sourceLocator());
        response.setBlockType(hit.blockType());
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

    public String getSourceName() {
        return sourceName;
    }

    public void setSourceName(String sourceName) {
        this.sourceName = sourceName;
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

    public String getQuoteText() {
        return quoteText;
    }

    public void setQuoteText(String quoteText) {
        this.quoteText = quoteText;
    }

    public Integer getQuoteStartOffset() {
        return quoteStartOffset;
    }

    public void setQuoteStartOffset(Integer quoteStartOffset) {
        this.quoteStartOffset = quoteStartOffset;
    }

    public Integer getQuoteEndOffset() {
        return quoteEndOffset;
    }

    public void setQuoteEndOffset(Integer quoteEndOffset) {
        this.quoteEndOffset = quoteEndOffset;
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

    public String getSectionPath() {
        return sectionPath;
    }

    public void setSectionPath(String sectionPath) {
        this.sectionPath = sectionPath;
    }

    public String getStructureType() {
        return structureType;
    }

    public void setStructureType(String structureType) {
        this.structureType = structureType;
    }

    public Integer getPageNumber() {
        return pageNumber;
    }

    public void setPageNumber(Integer pageNumber) {
        this.pageNumber = pageNumber;
    }

    public String getSourceLocator() {
        return sourceLocator;
    }

    public void setSourceLocator(String sourceLocator) {
        this.sourceLocator = sourceLocator;
    }

    public String getBlockType() {
        return blockType;
    }

    public void setBlockType(String blockType) {
        this.blockType = blockType;
    }
}
