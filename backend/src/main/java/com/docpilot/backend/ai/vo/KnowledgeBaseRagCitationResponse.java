package com.docpilot.backend.ai.vo;

import com.docpilot.backend.ai.rag.KnowledgeBaseRagEvidenceCitation;

public class KnowledgeBaseRagCitationResponse {

    private Integer index;
    private Long knowledgeBaseId;
    private Long documentId;
    private String documentTitle;
    private Integer indexVersion;
    private Long chunkId;
    private Integer chunkIndex;
    private Integer startOffset;
    private Integer endOffset;
    private String contentHash;
    private String snippet;
    private String quoteText;
    private Integer quoteStartOffset;
    private Integer quoteEndOffset;
    private String sectionPath;
    private String structureType;
    private Integer pageNumber;
    private String sourceLocator;
    private String blockType;
    private Double score;
    private Double vectorScore;
    private Double keywordScore;
    private Double fusedScore;
    private Double rerankScore;

    public static KnowledgeBaseRagCitationResponse from(KnowledgeBaseRagEvidenceCitation citation) {
        KnowledgeBaseRagCitationResponse response = new KnowledgeBaseRagCitationResponse();
        response.setIndex(citation.index());
        response.setKnowledgeBaseId(citation.knowledgeBaseId());
        response.setDocumentId(citation.documentId());
        response.setDocumentTitle(citation.documentTitle());
        response.setIndexVersion(citation.indexVersion());
        response.setChunkId(citation.chunkId());
        response.setChunkIndex(citation.chunkIndex());
        response.setStartOffset(citation.startOffset());
        response.setEndOffset(citation.endOffset());
        response.setContentHash(citation.contentHash());
        response.setSnippet(citation.snippet());
        response.setQuoteText(citation.quoteText());
        response.setQuoteStartOffset(citation.quoteStartOffset());
        response.setQuoteEndOffset(citation.quoteEndOffset());
        response.setSectionPath(citation.sectionPath());
        response.setStructureType(citation.structureType());
        response.setPageNumber(citation.pageNumber());
        response.setSourceLocator(citation.sourceLocator());
        response.setBlockType(citation.blockType());
        response.setScore(citation.score());
        response.setVectorScore(citation.vectorScore());
        response.setKeywordScore(citation.keywordScore());
        response.setFusedScore(citation.fusedScore());
        response.setRerankScore(citation.rerankScore());
        return response;
    }

    public Integer getIndex() {
        return index;
    }

    public void setIndex(Integer index) {
        this.index = index;
    }

    public Long getKnowledgeBaseId() {
        return knowledgeBaseId;
    }

    public void setKnowledgeBaseId(Long knowledgeBaseId) {
        this.knowledgeBaseId = knowledgeBaseId;
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

    public Integer getIndexVersion() {
        return indexVersion;
    }

    public void setIndexVersion(Integer indexVersion) {
        this.indexVersion = indexVersion;
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

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public String getSnippet() {
        return snippet;
    }

    public void setSnippet(String snippet) {
        this.snippet = snippet;
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

    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
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
