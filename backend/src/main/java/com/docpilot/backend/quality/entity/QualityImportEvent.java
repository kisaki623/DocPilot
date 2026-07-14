package com.docpilot.backend.quality.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("tb_quality_import_event")
public class QualityImportEvent {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("source_type")
    private String sourceType;

    @TableField("source_root_key")
    private String sourceRootKey;

    @TableField("source_relative_path")
    private String sourceRelativePath;

    @TableField("artifact_sha256")
    private String artifactSha256;

    private String marker;

    private String status;

    @TableField("safe_message")
    private String safeMessage;

    @TableField("requested_by_user_id")
    private Long requestedByUserId;

    @TableField("started_at")
    private LocalDateTime startedAt;

    @TableField("finished_at")
    private LocalDateTime finishedAt;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getSourceRootKey() { return sourceRootKey; }
    public void setSourceRootKey(String sourceRootKey) { this.sourceRootKey = sourceRootKey; }
    public String getSourceRelativePath() { return sourceRelativePath; }
    public void setSourceRelativePath(String sourceRelativePath) { this.sourceRelativePath = sourceRelativePath; }
    public String getArtifactSha256() { return artifactSha256; }
    public void setArtifactSha256(String artifactSha256) { this.artifactSha256 = artifactSha256; }
    public String getMarker() { return marker; }
    public void setMarker(String marker) { this.marker = marker; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSafeMessage() { return safeMessage; }
    public void setSafeMessage(String safeMessage) { this.safeMessage = safeMessage; }
    public Long getRequestedByUserId() { return requestedByUserId; }
    public void setRequestedByUserId(Long requestedByUserId) { this.requestedByUserId = requestedByUserId; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
