package com.docpilot.backend.file.vo;

import java.util.ArrayList;
import java.util.List;

public class ChunkUploadInitResponse {

    private String uploadId;
    private Integer totalChunks;
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

    public List<Integer> getUploadedChunkIndexes() {
        return uploadedChunkIndexes;
    }

    public void setUploadedChunkIndexes(List<Integer> uploadedChunkIndexes) {
        this.uploadedChunkIndexes = uploadedChunkIndexes;
    }
}

