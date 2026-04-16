package com.docpilot.backend.document.vo;

import java.time.LocalDateTime;

public class DocumentListItemResponse {

    private Long documentId;
    private Long fileRecordId;
    private String fileName;
    private String fileType;
    private String parseStatus;
    private String parseStatusLabel;
    private String parseStatusDescription;
    private String summary;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

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

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public String getParseStatus() {
        return parseStatus;
    }

    public void setParseStatus(String parseStatus) {
        this.parseStatus = parseStatus;
    }

    public String getParseStatusLabel() {
        return parseStatusLabel;
    }

    public void setParseStatusLabel(String parseStatusLabel) {
        this.parseStatusLabel = parseStatusLabel;
    }

    public String getParseStatusDescription() {
        return parseStatusDescription;
    }

    public void setParseStatusDescription(String parseStatusDescription) {
        this.parseStatusDescription = parseStatusDescription;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
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

