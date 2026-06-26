package com.docpilot.backend.conversation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("tb_conversation_summary")
public class ConversationSummary {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("conversation_id")
    private Long conversationId;

    @TableField("user_id")
    private Long userId;

    @TableField("summary")
    private String summary;

    @TableField("covered_start_seq")
    private Integer coveredStartSeq;

    @TableField("covered_end_seq")
    private Integer coveredEndSeq;

    @TableField("summary_version")
    private Integer summaryVersion;

    @TableField("status")
    private String status;

    @TableField("token_count")
    private Integer tokenCount;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;

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

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public Integer getCoveredStartSeq() {
        return coveredStartSeq;
    }

    public void setCoveredStartSeq(Integer coveredStartSeq) {
        this.coveredStartSeq = coveredStartSeq;
    }

    public Integer getCoveredEndSeq() {
        return coveredEndSeq;
    }

    public void setCoveredEndSeq(Integer coveredEndSeq) {
        this.coveredEndSeq = coveredEndSeq;
    }

    public Integer getSummaryVersion() {
        return summaryVersion;
    }

    public void setSummaryVersion(Integer summaryVersion) {
        this.summaryVersion = summaryVersion;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getTokenCount() {
        return tokenCount;
    }

    public void setTokenCount(Integer tokenCount) {
        this.tokenCount = tokenCount;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}
