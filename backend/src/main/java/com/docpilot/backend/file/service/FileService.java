package com.docpilot.backend.file.service;

import com.docpilot.backend.file.dto.ChunkUploadInitRequest;
import com.docpilot.backend.file.vo.FileUploadResponse;
import com.docpilot.backend.file.vo.ChunkUploadInitResponse;
import com.docpilot.backend.file.vo.ChunkUploadStatusResponse;
import org.springframework.web.multipart.MultipartFile;

public interface FileService {

    FileUploadResponse upload(MultipartFile file, Long userId);

    ChunkUploadInitResponse initChunkUpload(ChunkUploadInitRequest request, Long userId);

    ChunkUploadStatusResponse uploadChunk(String uploadId, Integer chunkIndex, MultipartFile chunk, Long userId);

    ChunkUploadStatusResponse getChunkUploadStatus(String uploadId, Long userId);

    FileUploadResponse completeChunkUpload(String uploadId, Long userId);
}

