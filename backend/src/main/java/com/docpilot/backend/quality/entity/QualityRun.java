package com.docpilot.backend.quality.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("tb_quality_run")
public class QualityRun {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String marker;

    private String status;

    private String environment;

    @TableField("data_source")
    private String dataSource;

    @TableField("source_root_key")
    private String sourceRootKey;

    @TableField("source_relative_path")
    private String sourceRelativePath;

    @TableField("source_sha256")
    private String sourceSha256;

    @TableField("artifact_name")
    private String artifactName;

    @TableField("artifact_updated_at")
    private LocalDateTime artifactUpdatedAt;

    @TableField("imported_at")
    private LocalDateTime importedAt;

    @TableField("import_revision")
    private Integer importRevision;

    @TableField("gate_count")
    private Integer gateCount;

    @TableField("failed_gate_count")
    private Integer failedGateCount;

    @TableField("review_gate_count")
    private Integer reviewGateCount;

    @TableField("eval_case_count")
    private Integer evalCaseCount;

    @TableField("trace_reference_count")
    private Integer traceReferenceCount;

    @TableField("prompt_tokens")
    private Integer promptTokens;

    @TableField("completion_tokens")
    private Integer completionTokens;

    @TableField("total_tokens")
    private Integer totalTokens;

    @TableField("estimated_cost")
    private BigDecimal estimatedCost;

    @TableField("failure_buckets_json")
    private String failureBucketsJson;

    @TableField("review_buckets_json")
    private String reviewBucketsJson;

    @TableField("diagnostics_json")
    private String diagnosticsJson;

    @TableField("trace_references_json")
    private String traceReferencesJson;

    @TableField("artifact_missing")
    private Boolean artifactMissing;

    @TableField("artifact_parse_failed")
    private Boolean artifactParseFailed;

    @TableField("redaction_status")
    private String redactionStatus;

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

    public String getMarker() {
        return marker;
    }

    public void setMarker(String marker) {
        this.marker = marker;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public String getDataSource() {
        return dataSource;
    }

    public void setDataSource(String dataSource) {
        this.dataSource = dataSource;
    }

    public String getSourceRootKey() {
        return sourceRootKey;
    }

    public void setSourceRootKey(String sourceRootKey) {
        this.sourceRootKey = sourceRootKey;
    }

    public String getSourceRelativePath() {
        return sourceRelativePath;
    }

    public void setSourceRelativePath(String sourceRelativePath) {
        this.sourceRelativePath = sourceRelativePath;
    }

    public String getSourceSha256() {
        return sourceSha256;
    }

    public void setSourceSha256(String sourceSha256) {
        this.sourceSha256 = sourceSha256;
    }

    public String getArtifactName() {
        return artifactName;
    }

    public void setArtifactName(String artifactName) {
        this.artifactName = artifactName;
    }

    public LocalDateTime getArtifactUpdatedAt() {
        return artifactUpdatedAt;
    }

    public void setArtifactUpdatedAt(LocalDateTime artifactUpdatedAt) {
        this.artifactUpdatedAt = artifactUpdatedAt;
    }

    public LocalDateTime getImportedAt() {
        return importedAt;
    }

    public void setImportedAt(LocalDateTime importedAt) {
        this.importedAt = importedAt;
    }

    public Integer getImportRevision() {
        return importRevision;
    }

    public void setImportRevision(Integer importRevision) {
        this.importRevision = importRevision;
    }

    public Integer getGateCount() {
        return gateCount;
    }

    public void setGateCount(Integer gateCount) {
        this.gateCount = gateCount;
    }

    public Integer getFailedGateCount() {
        return failedGateCount;
    }

    public void setFailedGateCount(Integer failedGateCount) {
        this.failedGateCount = failedGateCount;
    }

    public Integer getReviewGateCount() {
        return reviewGateCount;
    }

    public void setReviewGateCount(Integer reviewGateCount) {
        this.reviewGateCount = reviewGateCount;
    }

    public Integer getEvalCaseCount() {
        return evalCaseCount;
    }

    public void setEvalCaseCount(Integer evalCaseCount) {
        this.evalCaseCount = evalCaseCount;
    }

    public Integer getTraceReferenceCount() {
        return traceReferenceCount;
    }

    public void setTraceReferenceCount(Integer traceReferenceCount) {
        this.traceReferenceCount = traceReferenceCount;
    }

    public Integer getPromptTokens() {
        return promptTokens;
    }

    public void setPromptTokens(Integer promptTokens) {
        this.promptTokens = promptTokens;
    }

    public Integer getCompletionTokens() {
        return completionTokens;
    }

    public void setCompletionTokens(Integer completionTokens) {
        this.completionTokens = completionTokens;
    }

    public Integer getTotalTokens() {
        return totalTokens;
    }

    public void setTotalTokens(Integer totalTokens) {
        this.totalTokens = totalTokens;
    }

    public BigDecimal getEstimatedCost() {
        return estimatedCost;
    }

    public void setEstimatedCost(BigDecimal estimatedCost) {
        this.estimatedCost = estimatedCost;
    }

    public String getFailureBucketsJson() {
        return failureBucketsJson;
    }

    public void setFailureBucketsJson(String failureBucketsJson) {
        this.failureBucketsJson = failureBucketsJson;
    }

    public String getReviewBucketsJson() {
        return reviewBucketsJson;
    }

    public void setReviewBucketsJson(String reviewBucketsJson) {
        this.reviewBucketsJson = reviewBucketsJson;
    }

    public String getDiagnosticsJson() {
        return diagnosticsJson;
    }

    public void setDiagnosticsJson(String diagnosticsJson) {
        this.diagnosticsJson = diagnosticsJson;
    }

    public String getTraceReferencesJson() {
        return traceReferencesJson;
    }

    public void setTraceReferencesJson(String traceReferencesJson) {
        this.traceReferencesJson = traceReferencesJson;
    }

    public Boolean getArtifactMissing() {
        return artifactMissing;
    }

    public void setArtifactMissing(Boolean artifactMissing) {
        this.artifactMissing = artifactMissing;
    }

    public Boolean getArtifactParseFailed() {
        return artifactParseFailed;
    }

    public void setArtifactParseFailed(Boolean artifactParseFailed) {
        this.artifactParseFailed = artifactParseFailed;
    }

    public String getRedactionStatus() {
        return redactionStatus;
    }

    public void setRedactionStatus(String redactionStatus) {
        this.redactionStatus = redactionStatus;
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
