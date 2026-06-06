package com.docpilot.backend.ai.vo;

import com.docpilot.backend.ai.rag.KnowledgeBaseRagRetrievalResult;

import java.util.List;
import java.util.Map;

public class KnowledgeBaseRagRetrievalResponse {

    private Long knowledgeBaseId;
    private String query;
    private Integer topK;
    private Integer indexVersion;
    private List<Long> documentIds;
    private Boolean noEvidence;
    private String provider;
    private String collection;
    private String embeddingModel;
    private Map<Long, Integer> documentHitCounts;
    private List<KnowledgeBaseRagRetrievalHitResponse> hits;
    private List<KnowledgeBaseRagCitationResponse> citations;

    public static KnowledgeBaseRagRetrievalResponse from(KnowledgeBaseRagRetrievalResult result) {
        KnowledgeBaseRagRetrievalResponse response = new KnowledgeBaseRagRetrievalResponse();
        response.setKnowledgeBaseId(result.knowledgeBaseId());
        response.setQuery(result.query());
        response.setTopK(result.topK());
        response.setIndexVersion(result.indexVersion());
        response.setDocumentIds(result.documentIds());
        response.setNoEvidence(result.noEvidence());
        response.setProvider(result.provider());
        response.setCollection(result.collection());
        response.setEmbeddingModel(result.embeddingModel());
        response.setDocumentHitCounts(result.documentHitCounts());
        response.setHits(result.hits().stream().map(KnowledgeBaseRagRetrievalHitResponse::from).toList());
        response.setCitations(result.citations().stream().map(KnowledgeBaseRagCitationResponse::from).toList());
        return response;
    }

    public Long getKnowledgeBaseId() {
        return knowledgeBaseId;
    }

    public void setKnowledgeBaseId(Long knowledgeBaseId) {
        this.knowledgeBaseId = knowledgeBaseId;
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

    public List<Long> getDocumentIds() {
        return documentIds;
    }

    public void setDocumentIds(List<Long> documentIds) {
        this.documentIds = documentIds;
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

    public Map<Long, Integer> getDocumentHitCounts() {
        return documentHitCounts;
    }

    public void setDocumentHitCounts(Map<Long, Integer> documentHitCounts) {
        this.documentHitCounts = documentHitCounts;
    }

    public List<KnowledgeBaseRagRetrievalHitResponse> getHits() {
        return hits;
    }

    public void setHits(List<KnowledgeBaseRagRetrievalHitResponse> hits) {
        this.hits = hits;
    }

    public List<KnowledgeBaseRagCitationResponse> getCitations() {
        return citations;
    }

    public void setCitations(List<KnowledgeBaseRagCitationResponse> citations) {
        this.citations = citations;
    }
}
