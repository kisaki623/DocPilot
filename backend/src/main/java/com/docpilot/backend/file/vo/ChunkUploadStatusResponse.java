package com.docpilot.backend.file.vo;

import java.util.ArrayList;
import java.util.List;

public class ChunkUploadStatusResponse {

    private String uploadId;
    private Integer totalChunks;
    private Integer uploadedCount;
    private List<Integer> uploadedChunkIndexes = new ArrayList<>();

    public String getUploadId() {
        return uploadId;
    }

    public void setUploadId(String uploadId) {
        this.uploadId = uploadId;
    }

    public Integer getTotalChunks() {
        return totalChunks;
    }

    public void setTotalChunks(Integer totalChunks) {
        this.totalChunks = totalChunks;
    }

    public Integer getUploadedCount() {
        return uploadedCount;
    }

    public void setUploadedCount(Integer uploadedCount) {
        this.uploadedCount = uploadedCount;
    }

    public List<Integer> getUploadedChunkIndexes() {
        return uploadedChunkIndexes;
    }

    public void setUploadedChunkIndexes(List<Integer> uploadedChunkIndexes) {
        this.uploadedChunkIndexes = uploadedChunkIndexes;
    }
}

