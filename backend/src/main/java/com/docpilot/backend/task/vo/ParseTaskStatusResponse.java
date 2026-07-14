package com.docpilot.backend.task.vo;

import java.time.LocalDateTime;

public class ParseTaskStatusResponse {

    private Long taskId;
    private Long userId;
    private Long documentId;
    private Long fileRecordId;
    private String status;
    private String statusLabel;
    private String statusDescription;
    private String documentParseStatus;
    private Boolean terminal;
    private Boolean processing;
    private Boolean retryAllowed;
    private Boolean reparseAllowed;
    private Boolean safeReindexAllowed;
    private Boolean contentOnlyReindexAllowed;
    private Boolean parsedContentPresent;
    private Boolean stale;
    private String staleReason;
    private String consumeStatus;
    private String outboxStatus;
    private Integer outboxRetryCount;
    private LocalDateTime outboxNextRetryTime;
    private String errorCode;
    private String failedStage;
    private String recoveryAction;
    private String recoveryDescription;
    private Integer retryCount;
    private String errorMsg;
    private LocalDateTime startTime;
    private LocalDateTime finishTime;
    private LocalDateTime updateTime;

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public Long getFileRecordId() {
        return fileRecordId;
    }

    public void setFileRecordId(Long fileRecordId) {
        this.fileRecordId = fileRecordId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getStatusLabel() {
        return statusLabel;
    }

    public void setStatusLabel(String statusLabel) {
        this.statusLabel = statusLabel;
    }

    public String getStatusDescription() {
        return statusDescription;
    }

    public void setStatusDescription(String statusDescription) {
        this.statusDescription = statusDescription;
    }

    public String getDocumentParseStatus() {
        return documentParseStatus;
    }

    public void setDocumentParseStatus(String documentParseStatus) {
        this.documentParseStatus = documentParseStatus;
    }

    public Boolean getTerminal() {
        return terminal;
    }

    public void setTerminal(Boolean terminal) {
        this.terminal = terminal;
    }

    public Boolean getProcessing() {
        return processing;
    }

    public void setProcessing(Boolean processing) {
        this.processing = processing;
    }

    public Boolean getRetryAllowed() {
        return retryAllowed;
    }

    public void setRetryAllowed(Boolean retryAllowed) {
        this.retryAllowed = retryAllowed;
    }

    public Boolean getReparseAllowed() {
        return reparseAllowed;
    }

    public void setReparseAllowed(Boolean reparseAllowed) {
        this.reparseAllowed = reparseAllowed;
    }

    public Boolean getSafeReindexAllowed() {
        return safeReindexAllowed;
    }

    public void setSafeReindexAllowed(Boolean safeReindexAllowed) {
        this.safeReindexAllowed = safeReindexAllowed;
    }

    public Boolean getContentOnlyReindexAllowed() {
        return contentOnlyReindexAllowed;
    }

    public void setContentOnlyReindexAllowed(Boolean contentOnlyReindexAllowed) {
        this.contentOnlyReindexAllowed = contentOnlyReindexAllowed;
    }

    public Boolean getParsedContentPresent() {
        return parsedContentPresent;
    }

    public void setParsedContentPresent(Boolean parsedContentPresent) {
        this.parsedContentPresent = parsedContentPresent;
    }

    public Boolean getStale() {
        return stale;
    }

    public void setStale(Boolean stale) {
        this.stale = stale;
    }

    public String getStaleReason() {
        return staleReason;
    }

    public void setStaleReason(String staleReason) {
        this.staleReason = staleReason;
    }

    public String getConsumeStatus() {
        return consumeStatus;
    }

    public void setConsumeStatus(String consumeStatus) {
        this.consumeStatus = consumeStatus;
    }

    public String getOutboxStatus() {
        return outboxStatus;
    }

    public void setOutboxStatus(String outboxStatus) {
        this.outboxStatus = outboxStatus;
    }

    public Integer getOutboxRetryCount() {
        return outboxRetryCount;
    }

    public void setOutboxRetryCount(Integer outboxRetryCount) {
        this.outboxRetryCount = outboxRetryCount;
    }

    public LocalDateTime getOutboxNextRetryTime() {
        return outboxNextRetryTime;
    }

    public void setOutboxNextRetryTime(LocalDateTime outboxNextRetryTime) {
        this.outboxNextRetryTime = outboxNextRetryTime;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getFailedStage() {
        return failedStage;
    }

    public void setFailedStage(String failedStage) {
        this.failedStage = failedStage;
    }

    public String getRecoveryAction() {
        return recoveryAction;
    }

    public void setRecoveryAction(String recoveryAction) {
        this.recoveryAction = recoveryAction;
    }

    public String getRecoveryDescription() {
        return recoveryDescription;
    }

    public void setRecoveryDescription(String recoveryDescription) {
        this.recoveryDescription = recoveryDescription;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public String getErrorMsg() {
        return errorMsg;
    }

    public void setErrorMsg(String errorMsg) {
        this.errorMsg = errorMsg;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getFinishTime() {
        return finishTime;
    }

    public void setFinishTime(LocalDateTime finishTime) {
        this.finishTime = finishTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}
