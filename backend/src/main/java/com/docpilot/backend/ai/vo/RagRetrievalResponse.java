package com.docpilot.backend.ai.vo;

import com.docpilot.backend.ai.rag.RagRetrievalResult;

import java.util.List;

public class RagRetrievalResponse {

    private Long documentId;
    private String query;
    private Integer topK;
    private Integer indexVersion;
    private Boolean noEvidence;
    private String provider;
    private String collection;
    private String embeddingModel;
    private List<RagRetrievalHitResponse> hits;
    private List<RagCitationResponse> citations;

    public static RagRetrievalResponse from(RagRetrievalResult result) {
        RagRetrievalResponse response = new RagRetrievalResponse();
        response.setDocumentId(result.documentId());
        response.setQuery(result.query());
        response.setTopK(result.topK());
        response.setIndexVersion(result.indexVersion());
        response.setNoEvidence(result.noEvidence());
        response.setProvider(result.provider());
        response.setCollection(result.collection());
        response.setEmbeddingModel(result.embeddingModel());
        response.setHits(result.hits().stream().map(RagRetrievalHitResponse::from).toList());
        response.setCitations(result.citations().stream().map(RagCitationResponse::from).toList());
        return response;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public Integer getTopK() {
        return topK;
    }

    public void setTopK(Integer topK) {
        this.topK = topK;
    }

    public Integer getIndexVersion() {
        return indexVersion;
    }

    public void setIndexVersion(Integer indexVersion) {
        this.indexVersion = indexVersion;
    }

    public Boolean getNoEvidence() {
        return noEvidence;
    }

    public void setNoEvidence(Boolean noEvidence) {
        this.noEvidence = noEvidence;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getCollection() {
        return collection;
    }

    public void setCollection(String collection) {
        this.collection = collection;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(String embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public List<RagRetrievalHitResponse> getHits() {
        return hits;
    }

    public void setHits(List<RagRetrievalHitResponse> hits) {
        this.hits = hits;
    }

    public List<RagCitationResponse> getCitations() {
        return citations;
    }

    public void setCitations(List<RagCitationResponse> citations) {
        this.citations = citations;
    }
}
