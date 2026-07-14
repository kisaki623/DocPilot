package com.docpilot.backend.quality.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("tb_quality_run_case")
public class QualityRunCase {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("run_id")
    private Long runId;

    @TableField("case_id")
    private String caseId;

    @TableField("case_type")
    private String caseType;

    private String status;

    private Boolean passed;

    @TableField("trace_id")
    private String traceId;

    @TableField("agent_run_id")
    private String agentRunId;

    @TableField("metrics_json")
    private String metricsJson;

    @TableField("flags_json")
    private String flagsJson;

    @TableField("failure_buckets_json")
    private String failureBucketsJson;

    @TableField("review_buckets_json")
    private String reviewBucketsJson;

    @TableField("sort_order")
    private Integer sortOrder;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getRunId() { return runId; }
    public void setRunId(Long runId) { this.runId = runId; }
    public String getCaseId() { return caseId; }
    public void setCaseId(String caseId) { this.caseId = caseId; }
    public String getCaseType() { return caseType; }
    public void setCaseType(String caseType) { this.caseType = caseType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Boolean getPassed() { return passed; }
    public void setPassed(Boolean passed) { this.passed = passed; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public String getAgentRunId() { return agentRunId; }
    public void setAgentRunId(String agentRunId) { this.agentRunId = agentRunId; }
    public String getMetricsJson() { return metricsJson; }
    public void setMetricsJson(String metricsJson) { this.metricsJson = metricsJson; }
    public String getFlagsJson() { return flagsJson; }
    public void setFlagsJson(String flagsJson) { this.flagsJson = flagsJson; }
    public String getFailureBucketsJson() { return failureBucketsJson; }
    public void setFailureBucketsJson(String failureBucketsJson) { this.failureBucketsJson = failureBucketsJson; }
    public String getReviewBucketsJson() { return reviewBucketsJson; }
    public void setReviewBucketsJson(String reviewBucketsJson) { this.reviewBucketsJson = reviewBucketsJson; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
