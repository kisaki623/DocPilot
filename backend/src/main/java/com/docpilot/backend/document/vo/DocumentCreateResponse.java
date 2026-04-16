package com.docpilot.backend.document.vo;

public class DocumentCreateResponse {

    private Long id;
    private Long userId;
    private Long fileRecordId;
    private String title;
    private String parseStatus;
    private Boolean reused;

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

    public Long getFileRecordId() {
        return fileRecordId;
    }

    public void setFileRecordId(Long fileRecordId) {
        this.fileRecordId = fileRecordId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getParseStatus() {
        return parseStatus;
    }

    public void setParseStatus(String parseStatus) {
        this.parseStatus = parseStatus;
    }

    public Boolean getReused() {
        return reused;
    }

    public void setReused(Boolean reused) {
        this.reused = reused;
    }
}

