package com.docpilot.backend.knowledge.vo;

import com.docpilot.backend.knowledge.entity.KnowledgeBase;

import java.time.LocalDateTime;

public class KnowledgeBaseResponse {

    private Long id;
    private Long userId;
    private String name;
    private String description;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public static KnowledgeBaseResponse from(KnowledgeBase knowledgeBase) {
        KnowledgeBaseResponse response = new KnowledgeBaseResponse();
        response.setId(knowledgeBase.getId());
        response.setUserId(knowledgeBase.getUserId());
        response.setName(knowledgeBase.getName());
        response.setDescription(knowledgeBase.getDescription());
        response.setStatus(knowledgeBase.getStatus());
        response.setCreateTime(knowledgeBase.getCreateTime());
        response.setUpdateTime(knowledgeBase.getUpdateTime());
        return response;
    }

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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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
