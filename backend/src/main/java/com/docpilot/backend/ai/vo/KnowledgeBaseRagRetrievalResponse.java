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
    private String retrievalMode;
    private Boolean rerankApplied;
    private String rerankModel;
    private String rerankFailureReason;
    private Boolean multiQueryApplied;
    private Integer queryVariantCount;
    private Integer queryDedupeCount;
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
        response.setRetrievalMode(result.retrievalMode());
        response.setRerankApplied(result.rerankApplied());
        response.setRerankModel(result.rerankModel());
        response.setRerankFailureReason(result.rerankFailureReason());
        response.setMultiQueryApplied(result.multiQueryApplied());
        response.setQueryVariantCount(result.queryVariantCount());
        response.setQueryDedupeCount(result.queryDedupeCount());
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

    public String getRetrievalMode() {
        return retrievalMode;
    }

    public void setRetrievalMode(String retrievalMode) {
        this.retrievalMode = retrievalMode;
    }

    public Boolean getRerankApplied() {
        return rerankApplied;
    }

    public void setRerankApplied(Boolean rerankApplied) {
        this.rerankApplied = rerankApplied;
    }

    public String getRerankModel() {
        return rerankModel;
    }

    public void setRerankModel(String rerankModel) {
        this.rerankModel = rerankModel;
    }

    public String getRerankFailureReason() {
        return rerankFailureReason;
    }

    public void setRerankFailureReason(String rerankFailureReason) {
        this.rerankFailureReason = rerankFailureReason;
    }

    public Boolean getMultiQueryApplied() {
        return multiQueryApplied;
    }

    public void setMultiQueryApplied(Boolean multiQueryApplied) {
        this.multiQueryApplied = multiQueryApplied;
    }

    public Integer getQueryVariantCount() {
        return queryVariantCount;
    }

    public void setQueryVariantCount(Integer queryVariantCount) {
        this.queryVariantCount = queryVariantCount;
    }

    public Integer getQueryDedupeCount() {
        return queryDedupeCount;
    }

    public void setQueryDedupeCount(Integer queryDedupeCount) {
        this.queryDedupeCount = queryDedupeCount;
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
