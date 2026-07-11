package com.docpilot.backend.ai.agent.vo;

import com.docpilot.backend.ai.agent.tool.KnowledgeBaseSearchTool;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class KnowledgeBaseAgentResponse {

    private String traceId;
    private String startedAt;
    private String finishedAt;
    private Long totalDurationMs;
    private boolean success;
    private Long knowledgeBaseId;
    private String task;
    private String decision;
    private String routingReason;
    private List<String> matchedKeywords = new ArrayList<>();
    private String finalAnswer;
    private boolean noEvidence;
    private boolean fallbackUsed;
    private String fallbackReason;
    private String answerProvider;
    private String answerModel;
    private int modelCallCount;
    private Map<Long, Integer> documentHitCounts = Map.of();
    private String retrievalMode;
    private boolean rerankApplied;
    private String rerankFailureReason;
    private boolean multiQueryApplied;
    private int queryVariantCount;
    private int queryDedupeCount;
    private List<KnowledgeBaseSearchTool.SearchHit> retrievalHits = new ArrayList<>();
    private List<KnowledgeBaseSearchTool.SearchCitation> citations = new ArrayList<>();
    private List<AgentStep> steps = new ArrayList<>();

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(String startedAt) {
        this.startedAt = startedAt;
    }

    public String getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(String finishedAt) {
        this.finishedAt = finishedAt;
    }

    public Long getTotalDurationMs() {
        return totalDurationMs;
    }

    public void setTotalDurationMs(Long totalDurationMs) {
        this.totalDurationMs = totalDurationMs;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public Long getKnowledgeBaseId() {
        return knowledgeBaseId;
    }

    public void setKnowledgeBaseId(Long knowledgeBaseId) {
        this.knowledgeBaseId = knowledgeBaseId;
    }

    public String getTask() {
        return task;
    }

    public void setTask(String task) {
        this.task = task;
    }

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public String getRoutingReason() {
        return routingReason;
    }

    public void setRoutingReason(String routingReason) {
        this.routingReason = routingReason;
    }

    public List<String> getMatchedKeywords() {
        return matchedKeywords;
    }

    public void setMatchedKeywords(List<String> matchedKeywords) {
        this.matchedKeywords = matchedKeywords == null ? new ArrayList<>() : new ArrayList<>(matchedKeywords);
    }

    public String getFinalAnswer() {
        return finalAnswer;
    }

    public void setFinalAnswer(String finalAnswer) {
        this.finalAnswer = finalAnswer;
    }

    public boolean isNoEvidence() {
        return noEvidence;
    }

    public void setNoEvidence(boolean noEvidence) {
        this.noEvidence = noEvidence;
    }

    public boolean isFallbackUsed() {
        return fallbackUsed;
    }

    public void setFallbackUsed(boolean fallbackUsed) {
        this.fallbackUsed = fallbackUsed;
    }

    public String getFallbackReason() {
        return fallbackReason;
    }

    public void setFallbackReason(String fallbackReason) {
        this.fallbackReason = fallbackReason;
    }

    public String getAnswerProvider() {
        return answerProvider;
    }

    public void setAnswerProvider(String answerProvider) {
        this.answerProvider = answerProvider;
    }

    public String getAnswerModel() {
        return answerModel;
    }

    public void setAnswerModel(String answerModel) {
        this.answerModel = answerModel;
    }

    public int getModelCallCount() {
        return modelCallCount;
    }

    public void setModelCallCount(int modelCallCount) {
        this.modelCallCount = Math.max(0, modelCallCount);
    }

    public Map<Long, Integer> getDocumentHitCounts() {
        return documentHitCounts;
    }

    public void setDocumentHitCounts(Map<Long, Integer> documentHitCounts) {
        this.documentHitCounts = documentHitCounts == null ? Map.of() : Map.copyOf(documentHitCounts);
    }

    public String getRetrievalMode() {
        return retrievalMode;
    }

    public void setRetrievalMode(String retrievalMode) {
        this.retrievalMode = retrievalMode;
    }

    public boolean isRerankApplied() {
        return rerankApplied;
    }

    public void setRerankApplied(boolean rerankApplied) {
        this.rerankApplied = rerankApplied;
    }

    public String getRerankFailureReason() {
        return rerankFailureReason;
    }

    public void setRerankFailureReason(String rerankFailureReason) {
        this.rerankFailureReason = rerankFailureReason;
    }

    public boolean isMultiQueryApplied() {
        return multiQueryApplied;
    }

    public void setMultiQueryApplied(boolean multiQueryApplied) {
        this.multiQueryApplied = multiQueryApplied;
    }

    public int getQueryVariantCount() {
        return queryVariantCount;
    }

    public void setQueryVariantCount(int queryVariantCount) {
        this.queryVariantCount = queryVariantCount;
    }

    public int getQueryDedupeCount() {
        return queryDedupeCount;
    }

    public void setQueryDedupeCount(int queryDedupeCount) {
        this.queryDedupeCount = queryDedupeCount;
    }

    public List<KnowledgeBaseSearchTool.SearchHit> getRetrievalHits() {
        return retrievalHits;
    }

    public void setRetrievalHits(List<KnowledgeBaseSearchTool.SearchHit> retrievalHits) {
        this.retrievalHits = retrievalHits == null ? new ArrayList<>() : new ArrayList<>(retrievalHits);
    }

    public List<KnowledgeBaseSearchTool.SearchCitation> getCitations() {
        return citations;
    }

    public void setCitations(List<KnowledgeBaseSearchTool.SearchCitation> citations) {
        this.citations = citations == null ? new ArrayList<>() : new ArrayList<>(citations);
    }

    public List<AgentStep> getSteps() {
        return steps;
    }

    public void setSteps(List<AgentStep> steps) {
        this.steps = steps == null ? new ArrayList<>() : new ArrayList<>(steps);
    }

    public static class AgentStep {
        private int stepIndex;
        private String toolName;
        private String status;
        private String inputSummary;
        private String outputSummary;
        private String errorMessage;
        private long durationMs;

        public int getStepIndex() {
            return stepIndex;
        }

        public void setStepIndex(int stepIndex) {
            this.stepIndex = stepIndex;
        }

        public String getToolName() {
            return toolName;
        }

        public void setToolName(String toolName) {
            this.toolName = toolName;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getInputSummary() {
            return inputSummary;
        }

        public void setInputSummary(String inputSummary) {
            this.inputSummary = inputSummary;
        }

        public String getOutputSummary() {
            return outputSummary;
        }

        public void setOutputSummary(String outputSummary) {
            this.outputSummary = outputSummary;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }

        public long getDurationMs() {
            return durationMs;
        }

        public void setDurationMs(long durationMs) {
            this.durationMs = durationMs;
        }
    }
}
