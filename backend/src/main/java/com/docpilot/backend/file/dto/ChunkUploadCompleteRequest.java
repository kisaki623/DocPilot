package com.docpilot.backend.file.dto;

import jakarta.validation.constraints.NotBlank;

public class ChunkUploadCompleteRequest {

    @NotBlank(message = "uploadId 不能为空")
    private String uploadId;

    public String getUploadId() {
        return uploadId;
    }

    public void setUploadId(String uploadId) {
        this.uploadId = uploadId;
    }
}

