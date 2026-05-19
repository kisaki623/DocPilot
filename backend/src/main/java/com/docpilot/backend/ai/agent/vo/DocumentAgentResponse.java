package com.docpilot.backend.ai.agent.vo;

import com.docpilot.backend.ai.vo.DocumentQaResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DocumentAgentResponse {

    private String traceId;
    private String startedAt;
    private String finishedAt;
    private Long totalDurationMs;
    private boolean success;
    private Long taskId;
    private Long documentId;
    private String task;
    private String sessionId;
    private String decision;
    private String primaryDecision;
    private String llmDecision;
    private String finalDecision;
    private boolean fallbackUsed;
    private String fallbackReason;
    private String executionMode;
    private String toolSelectionSource;
    private String routingReason;
    private List<String> matchedKeywords = new ArrayList<>();
    private String finalAnswer;
    private List<DocumentQaResponse.CitationItem> citations = new ArrayList<>();
    private List<RagRetrievedChunk> ragResults = new ArrayList<>();
    private String ragAnswerContext;
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

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public String getTask() {
        return task;
    }

    public void setTask(String task) {
        this.task = task;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public String getPrimaryDecision() {
        return primaryDecision;
    }

    public void setPrimaryDecision(String primaryDecision) {
        this.primaryDecision = primaryDecision;
    }

    public String getLlmDecision() {
        return llmDecision;
    }

    public void setLlmDecision(String llmDecision) {
        this.llmDecision = llmDecision;
    }

    public String getFinalDecision() {
        return finalDecision;
    }

    public void setFinalDecision(String finalDecision) {
        this.finalDecision = finalDecision;
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

    public String getExecutionMode() {
        return executionMode;
    }

    public void setExecutionMode(String executionMode) {
        this.executionMode = executionMode;
    }

    public String getToolSelectionSource() {
        return toolSelectionSource;
    }

    public void setToolSelectionSource(String toolSelectionSource) {
        this.toolSelectionSource = toolSelectionSource;
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
        this.matchedKeywords = matchedKeywords;
    }

    public String getFinalAnswer() {
        return finalAnswer;
    }

    public void setFinalAnswer(String finalAnswer) {
        this.finalAnswer = finalAnswer;
    }

    public List<DocumentQaResponse.CitationItem> getCitations() {
        return citations;
    }

    public void setCitations(List<DocumentQaResponse.CitationItem> citations) {
        this.citations = citations;
    }

    public List<RagRetrievedChunk> getRagResults() {
        return ragResults;
    }

    public void setRagResults(List<RagRetrievedChunk> ragResults) {
        this.ragResults = ragResults;
    }

    public String getRagAnswerContext() {
        return ragAnswerContext;
    }

    public void setRagAnswerContext(String ragAnswerContext) {
        this.ragAnswerContext = ragAnswerContext;
    }

    public List<AgentStep> getSteps() {
        return steps;
    }

    public void setSteps(List<AgentStep> steps) {
        this.steps = steps;
    }

    public static class AgentStep {
        private int stepIndex;
        private String toolName;
        private String status;
        private String inputSummary;
        private String outputSummary;
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

        public long getDurationMs() {
            return durationMs;
        }

        public void setDurationMs(long durationMs) {
            this.durationMs = durationMs;
        }
    }

    public static class RagRetrievedChunk {
        private int rank;
        private int chunkIndex;
        private double score;
        private String snippet;
        private Map<String, String> metadata;

        public int getRank() {
            return rank;
        }

        public void setRank(int rank) {
            this.rank = rank;
        }

        public int getChunkIndex() {
            return chunkIndex;
        }

        public void setChunkIndex(int chunkIndex) {
            this.chunkIndex = chunkIndex;
        }

        public double getScore() {
            return score;
        }

        public void setScore(double score) {
            this.score = score;
        }

        public String getSnippet() {
            return snippet;
        }

        public void setSnippet(String snippet) {
            this.snippet = snippet;
        }

        public Map<String, String> getMetadata() {
            return metadata;
        }

        public void setMetadata(Map<String, String> metadata) {
            this.metadata = metadata;
        }
    }
}
