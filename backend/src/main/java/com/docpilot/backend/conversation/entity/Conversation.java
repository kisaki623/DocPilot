package com.docpilot.backend.conversation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("tb_conversation")
public class Conversation {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("title")
    private String title;

    @TableField("context_mode")
    private String contextMode;

    @TableField("status")
    private String status;

    @TableField("bound_knowledge_base_id")
    private Long boundKnowledgeBaseId;

    @TableField("summary_enabled")
    private Boolean summaryEnabled;

    @TableField("memory_enabled")
    private Boolean memoryEnabled;

    @TableField("last_message_time")
    private LocalDateTime lastMessageTime;

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

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContextMode() {
        return contextMode;
    }

    public void setContextMode(String contextMode) {
        this.contextMode = contextMode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getBoundKnowledgeBaseId() {
        return boundKnowledgeBaseId;
    }

    public void setBoundKnowledgeBaseId(Long boundKnowledgeBaseId) {
        this.boundKnowledgeBaseId = boundKnowledgeBaseId;
    }

    public Boolean getSummaryEnabled() {
        return summaryEnabled;
    }

    public void setSummaryEnabled(Boolean summaryEnabled) {
        this.summaryEnabled = summaryEnabled;
    }

    public Boolean getMemoryEnabled() {
        return memoryEnabled;
    }

    public void setMemoryEnabled(Boolean memoryEnabled) {
        this.memoryEnabled = memoryEnabled;
    }

    public LocalDateTime getLastMessageTime() {
        return lastMessageTime;
    }

    public void setLastMessageTime(LocalDateTime lastMessageTime) {
        this.lastMessageTime = lastMessageTime;
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
