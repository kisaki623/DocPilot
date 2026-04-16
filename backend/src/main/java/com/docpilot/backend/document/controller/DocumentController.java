package com.docpilot.backend.document.controller;

import com.docpilot.backend.common.api.ApiResponse;
import com.docpilot.backend.common.context.UserHolder;
import com.docpilot.backend.document.dto.DocumentCreateRequest;
import com.docpilot.backend.document.dto.DocumentListRequest;
import com.docpilot.backend.document.service.DocumentService;
import com.docpilot.backend.document.vo.DocumentCreateResponse;
import com.docpilot.backend.document.vo.DocumentDetailResponse;
import com.docpilot.backend.document.vo.DocumentListResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/document")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping("/create")
    public ApiResponse<DocumentCreateResponse> create(@RequestBody DocumentCreateRequest request) {
        Long userId = UserHolder.requireUserId();
        return ApiResponse.success(documentService.create(request.getFileRecordId(), userId));
    }

    @GetMapping("/list")
    public ApiResponse<DocumentListResponse> list(DocumentListRequest request) {
        Long userId = UserHolder.requireUserId();
        return ApiResponse.success(documentService.listByUser(userId, request.getPageNo(), request.getPageSize()));
    }

    @GetMapping("/detail")
    public ApiResponse<DocumentDetailResponse> detail(@RequestParam("documentId") Long documentId) {
        Long userId = UserHolder.requireUserId();
        return ApiResponse.success(documentService.getDetailById(documentId, userId));
    }
}

