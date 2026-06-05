package com.docpilot.backend.ai.vo;

import com.docpilot.backend.ai.rag.KnowledgeBaseRagQaAnswer;

import java.util.List;

public class KnowledgeBaseRagQaResponse {

    private Long knowledgeBaseId;
    private String question;
    private String answer;
    private String sessionId;
    private Boolean noEvidence;
    private Boolean fallbackUsed;
    private String fallbackReason;
    private KnowledgeBaseRagRetrievalResponse retrieval;
    private List<KnowledgeBaseRagCitationResponse> citations;

    public static KnowledgeBaseRagQaResponse from(KnowledgeBaseRagQaAnswer answer) {
        KnowledgeBaseRagQaResponse response = new KnowledgeBaseRagQaResponse();
        response.setKnowledgeBaseId(answer.knowledgeBaseId());
        response.setQuestion(answer.question());
        response.setAnswer(answer.answer());
        response.setSessionId(answer.sessionId());
        response.setNoEvidence(answer.noEvidence());
        response.setFallbackUsed(answer.fallbackUsed());
        response.setFallbackReason(answer.fallbackReason());
        if (answer.retrieval() != null) {
            response.setRetrieval(KnowledgeBaseRagRetrievalResponse.from(answer.retrieval()));
            response.setCitations(response.getRetrieval().getCitations());
        } else {
            response.setCitations(List.of());
        }
        return response;
    }

    public Long getKnowledgeBaseId() {
        return knowledgeBaseId;
    }

    public void setKnowledgeBaseId(Long knowledgeBaseId) {
        this.knowledgeBaseId = knowledgeBaseId;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public Boolean getNoEvidence() {
        return noEvidence;
    }

    public void setNoEvidence(Boolean noEvidence) {
        this.noEvidence = noEvidence;
    }

    public Boolean getFallbackUsed() {
        return fallbackUsed;
    }

    public void setFallbackUsed(Boolean fallbackUsed) {
        this.fallbackUsed = fallbackUsed;
    }

    public String getFallbackReason() {
        return fallbackReason;
    }

    public void setFallbackReason(String fallbackReason) {
        this.fallbackReason = fallbackReason;
    }

    public KnowledgeBaseRagRetrievalResponse getRetrieval() {
        return retrieval;
    }

    public void setRetrieval(KnowledgeBaseRagRetrievalResponse retrieval) {
        this.retrieval = retrieval;
    }

    public List<KnowledgeBaseRagCitationResponse> getCitations() {
        return citations;
    }

    public void setCitations(List<KnowledgeBaseRagCitationResponse> citations) {
        this.citations = citations;
    }
}
