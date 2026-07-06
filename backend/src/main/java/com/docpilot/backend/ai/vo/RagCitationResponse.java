package com.docpilot.backend.ai.vo;

import com.docpilot.backend.ai.rag.RagEvidenceCitation;

public class RagCitationResponse {

    private Integer index;
    private Long documentId;
    private String sourceName;
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
    private Double score;

    public static RagCitationResponse from(RagEvidenceCitation citation) {
        RagCitationResponse response = new RagCitationResponse();
        response.setIndex(citation.index());
        response.setDocumentId(citation.documentId());
        response.setSourceName(citation.sourceName());
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
        response.setScore(citation.score());
        return response;
    }

    public Integer getIndex() {
        return index;
    }

    public void setIndex(Integer index) {
        this.index = index;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public String getSourceName() {
        return sourceName;
    }

    public void setSourceName(String sourceName) {
        this.sourceName = sourceName;
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

    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
    }
}
