package com.docpilot.backend.conversation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("tb_context_trace")
public class ConversationContextTrace {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("conversation_id")
    private Long conversationId;

    @TableField("message_id")
    private Long messageId;

    @TableField("user_id")
    private Long userId;

    @TableField("context_mode")
    private String contextMode;

    @TableField("summary_used")
    private Boolean summaryUsed;

    @TableField("recent_turn_count")
    private Integer recentTurnCount;

    @TableField("recent_message_count")
    private Integer recentMessageCount;

    @TableField("memory_used")
    private Boolean memoryUsed;

    @TableField("memory_count")
    private Integer memoryCount;

    @TableField("memory_types_json")
    private String memoryTypesJson;

    @TableField("rag_triggered")
    private Boolean ragTriggered;

    @TableField("rag_required")
    private Boolean ragRequired;

    @TableField("knowledge_base_id")
    private Long knowledgeBaseId;

    @TableField("evidence_count")
    private Integer evidenceCount;

    @TableField("no_evidence")
    private Boolean noEvidence;

    @TableField("document_hit_counts_json")
    private String documentHitCountsJson;

    @TableField("max_prompt_tokens")
    private Integer maxPromptTokens;

    @TableField("estimated_prompt_tokens")
    private Integer estimatedPromptTokens;

    @TableField("truncated")
    private Boolean truncated;

    @TableField("truncated_types_json")
    private String truncatedTypesJson;

    @TableField("fallback_used")
    private Boolean fallbackUsed;

    @TableField("fallback_reason")
    private String fallbackReason;

    @TableField("model_call_skipped")
    private Boolean modelCallSkipped;

    @TableField("create_time")
    private LocalDateTime createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getConversationId() {
        return conversationId;
    }

    public void setConversationId(Long conversationId) {
        this.conversationId = conversationId;
    }

    public Long getMessageId() {
        return messageId;
    }

    public void setMessageId(Long messageId) {
        this.messageId = messageId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getContextMode() {
        return contextMode;
    }

    public void setContextMode(String contextMode) {
        this.contextMode = contextMode;
    }

    public Boolean getSummaryUsed() {
        return summaryUsed;
    }

    public void setSummaryUsed(Boolean summaryUsed) {
        this.summaryUsed = summaryUsed;
    }

    public Integer getRecentTurnCount() {
        return recentTurnCount;
    }

    public void setRecentTurnCount(Integer recentTurnCount) {
        this.recentTurnCount = recentTurnCount;
    }

    public Integer getRecentMessageCount() {
        return recentMessageCount;
    }

    public void setRecentMessageCount(Integer recentMessageCount) {
        this.recentMessageCount = recentMessageCount;
    }

    public Boolean getMemoryUsed() {
        return memoryUsed;
    }

    public void setMemoryUsed(Boolean memoryUsed) {
        this.memoryUsed = memoryUsed;
    }

    public Integer getMemoryCount() {
        return memoryCount;
    }

    public void setMemoryCount(Integer memoryCount) {
        this.memoryCount = memoryCount;
    }

    public String getMemoryTypesJson() {
        return memoryTypesJson;
    }

    public void setMemoryTypesJson(String memoryTypesJson) {
        this.memoryTypesJson = memoryTypesJson;
    }

    public Boolean getRagTriggered() {
        return ragTriggered;
    }

    public void setRagTriggered(Boolean ragTriggered) {
        this.ragTriggered = ragTriggered;
    }

    public Boolean getRagRequired() {
        return ragRequired;
    }

    public void setRagRequired(Boolean ragRequired) {
        this.ragRequired = ragRequired;
    }

    public Long getKnowledgeBaseId() {
        return knowledgeBaseId;
    }

    public void setKnowledgeBaseId(Long knowledgeBaseId) {
        this.knowledgeBaseId = knowledgeBaseId;
    }

    public Integer getEvidenceCount() {
        return evidenceCount;
    }

    public void setEvidenceCount(Integer evidenceCount) {
        this.evidenceCount = evidenceCount;
    }

    public Boolean getNoEvidence() {
        return noEvidence;
    }

    public void setNoEvidence(Boolean noEvidence) {
        this.noEvidence = noEvidence;
    }

    public String getDocumentHitCountsJson() {
        return documentHitCountsJson;
    }

    public void setDocumentHitCountsJson(String documentHitCountsJson) {
        this.documentHitCountsJson = documentHitCountsJson;
    }

    public Integer getMaxPromptTokens() {
        return maxPromptTokens;
    }

    public void setMaxPromptTokens(Integer maxPromptTokens) {
        this.maxPromptTokens = maxPromptTokens;
    }

    public Integer getEstimatedPromptTokens() {
        return estimatedPromptTokens;
    }

    public void setEstimatedPromptTokens(Integer estimatedPromptTokens) {
        this.estimatedPromptTokens = estimatedPromptTokens;
    }

    public Boolean getTruncated() {
        return truncated;
    }

    public void setTruncated(Boolean truncated) {
        this.truncated = truncated;
    }

    public String getTruncatedTypesJson() {
        return truncatedTypesJson;
    }

    public void setTruncatedTypesJson(String truncatedTypesJson) {
        this.truncatedTypesJson = truncatedTypesJson;
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

    public Boolean getModelCallSkipped() {
        return modelCallSkipped;
    }

    public void setModelCallSkipped(Boolean modelCallSkipped) {
        this.modelCallSkipped = modelCallSkipped;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
