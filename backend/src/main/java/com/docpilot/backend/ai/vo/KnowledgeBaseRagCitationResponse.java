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
    private Double score;

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
        response.setScore(citation.score());
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

    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
    }
}
