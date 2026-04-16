package com.docpilot.backend.mq.message;

public class ParseTaskMessage {

    private String messageKey;
    private Long taskId;
    private Long documentId;
    private Long fileRecordId;

    public String getMessageKey() {
        return messageKey;
    }

    public void setMessageKey(String messageKey) {
        this.messageKey = messageKey;
    }

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
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
}

