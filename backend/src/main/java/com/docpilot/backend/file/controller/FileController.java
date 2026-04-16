package com.docpilot.backend.file.controller;

import com.docpilot.backend.common.api.ApiResponse;
import com.docpilot.backend.common.context.UserHolder;
import com.docpilot.backend.file.dto.ChunkUploadCompleteRequest;
import com.docpilot.backend.file.dto.ChunkUploadInitRequest;
import com.docpilot.backend.file.service.FileService;
import com.docpilot.backend.file.vo.ChunkUploadInitResponse;
import com.docpilot.backend.file.vo.ChunkUploadStatusResponse;
import com.docpilot.backend.file.vo.FileUploadResponse;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/file")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<FileUploadResponse> upload(@RequestPart("file") MultipartFile file) {
        Long userId = UserHolder.requireUserId();
        return ApiResponse.success(fileService.upload(file, userId));
    }

    @PostMapping("/upload/chunk/init")
    public ApiResponse<ChunkUploadInitResponse> initChunkUpload(@Valid @RequestBody ChunkUploadInitRequest request) {
        Long userId = UserHolder.requireUserId();
        return ApiResponse.success(fileService.initChunkUpload(request, userId));
    }

    @PostMapping(value = "/upload/chunk", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ChunkUploadStatusResponse> uploadChunk(@RequestPart("uploadId") String uploadId,
                                                              @RequestPart("chunkIndex") Integer chunkIndex,
                                                              @RequestPart("chunk") MultipartFile chunk) {
        Long userId = UserHolder.requireUserId();
        return ApiResponse.success(fileService.uploadChunk(uploadId, chunkIndex, chunk, userId));
    }

    @GetMapping("/upload/chunk/status")
    public ApiResponse<ChunkUploadStatusResponse> getChunkStatus(@RequestParam("uploadId") String uploadId) {
        Long userId = UserHolder.requireUserId();
        return ApiResponse.success(fileService.getChunkUploadStatus(uploadId, userId));
    }

    @PostMapping("/upload/chunk/complete")
    public ApiResponse<FileUploadResponse> completeChunkUpload(@Valid @RequestBody ChunkUploadCompleteRequest request) {
        Long userId = UserHolder.requireUserId();
        return ApiResponse.success(fileService.completeChunkUpload(request.getUploadId(), userId));
    }
}

