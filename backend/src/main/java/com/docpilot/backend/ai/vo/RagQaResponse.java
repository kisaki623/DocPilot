package com.docpilot.backend.ai.vo;

import com.docpilot.backend.ai.rag.RagQaAnswer;

import java.util.List;

public class RagQaResponse {

    private Long documentId;
    private String question;
    private String answer;
    private String sessionId;
    private Boolean noEvidence;
    private Boolean fallbackUsed;
    private String fallbackReason;
    private RagRetrievalResponse retrieval;
    private List<RagCitationResponse> citations;

    public static RagQaResponse from(RagQaAnswer answer) {
        RagQaResponse response = new RagQaResponse();
        response.setDocumentId(answer.documentId());
        response.setQuestion(answer.question());
        response.setAnswer(answer.answer());
        response.setSessionId(answer.sessionId());
        response.setNoEvidence(answer.noEvidence());
        response.setFallbackUsed(answer.fallbackUsed());
        response.setFallbackReason(answer.fallbackReason());
        if (answer.retrieval() != null) {
            response.setRetrieval(RagRetrievalResponse.from(answer.retrieval()));
            response.setCitations(response.getRetrieval().getCitations());
        } else {
            response.setCitations(List.of());
        }
        return response;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
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

    public RagRetrievalResponse getRetrieval() {
        return retrieval;
    }

    public void setRetrieval(RagRetrievalResponse retrieval) {
        this.retrieval = retrieval;
    }

    public List<RagCitationResponse> getCitations() {
        return citations;
    }

    public void setCitations(List<RagCitationResponse> citations) {
        this.citations = citations;
    }
}
