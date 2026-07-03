package com.docpilot.backend.ai.vo;

import com.docpilot.backend.ai.rag.KnowledgeBaseRagRetrievalHit;

public class KnowledgeBaseRagRetrievalHitResponse {

    private Integer citationIndex;
    private Long knowledgeBaseId;
    private String vectorId;
    private Double score;
    private Long documentId;
    private String documentTitle;
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
    private Double vectorScore;
    private Double keywordScore;
    private Double fusedScore;
    private Double rerankScore;

    public static KnowledgeBaseRagRetrievalHitResponse from(KnowledgeBaseRagRetrievalHit hit) {
        KnowledgeBaseRagRetrievalHitResponse response = new KnowledgeBaseRagRetrievalHitResponse();
        response.setCitationIndex(hit.citationIndex());
        response.setKnowledgeBaseId(hit.knowledgeBaseId());
        response.setVectorId(hit.vectorId());
        response.setScore(hit.score());
        response.setDocumentId(hit.documentId());
        response.setDocumentTitle(hit.documentTitle());
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
        response.setVectorScore(hit.vectorScore());
        response.setKeywordScore(hit.keywordScore());
        response.setFusedScore(hit.fusedScore());
        response.setRerankScore(hit.rerankScore());
        return response;
    }

    public Integer getCitationIndex() {
        return citationIndex;
    }

    public void setCitationIndex(Integer citationIndex) {
        this.citationIndex = citationIndex;
    }

    public Long getKnowledgeBaseId() {
        return knowledgeBaseId;
    }

    public void setKnowledgeBaseId(Long knowledgeBaseId) {
        this.knowledgeBaseId = knowledgeBaseId;
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

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public String getDocumentTitle() {
        return documentTitle;
    }

    public void setDocumentTitle(String documentTitle) {
        this.documentTitle = documentTitle;
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

    public Double getVectorScore() {
        return vectorScore;
    }

    public void setVectorScore(Double vectorScore) {
        this.vectorScore = vectorScore;
    }

    public Double getKeywordScore() {
        return keywordScore;
    }

    public void setKeywordScore(Double keywordScore) {
        this.keywordScore = keywordScore;
    }

    public Double getFusedScore() {
        return fusedScore;
    }

    public void setFusedScore(Double fusedScore) {
        this.fusedScore = fusedScore;
    }

    public Double getRerankScore() {
        return rerankScore;
    }

    public void setRerankScore(Double rerankScore) {
        this.rerankScore = rerankScore;
    }
}
