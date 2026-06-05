package com.docpilot.backend.knowledge.controller;

import com.docpilot.backend.common.api.ApiResponse;
import com.docpilot.backend.common.context.UserHolder;
import com.docpilot.backend.knowledge.dto.KnowledgeBaseAddDocumentsRequest;
import com.docpilot.backend.knowledge.dto.KnowledgeBaseCreateRequest;
import com.docpilot.backend.knowledge.service.KnowledgeBaseService;
import com.docpilot.backend.knowledge.vo.KnowledgeBaseDetailResponse;
import com.docpilot.backend.knowledge.vo.KnowledgeBaseDocumentMutationResponse;
import com.docpilot.backend.knowledge.vo.KnowledgeBaseResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/knowledge-bases")
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    public KnowledgeBaseController(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @PostMapping
    public ApiResponse<KnowledgeBaseResponse> create(@RequestBody KnowledgeBaseCreateRequest request) {
        Long userId = UserHolder.requireUserId();
        return ApiResponse.success(knowledgeBaseService.create(
                userId,
                request == null ? null : request.getName(),
                request == null ? null : request.getDescription()
        ));
    }

    @GetMapping
    public ApiResponse<List<KnowledgeBaseResponse>> list() {
        Long userId = UserHolder.requireUserId();
        return ApiResponse.success(knowledgeBaseService.listByUser(userId));
    }

    @GetMapping("/{knowledgeBaseId}")
    public ApiResponse<KnowledgeBaseDetailResponse> detail(
            @PathVariable("knowledgeBaseId") Long knowledgeBaseId) {
        Long userId = UserHolder.requireUserId();
        return ApiResponse.success(knowledgeBaseService.getDetail(userId, knowledgeBaseId));
    }

    @PostMapping("/{knowledgeBaseId}/documents")
    public ApiResponse<KnowledgeBaseDocumentMutationResponse> addDocuments(
            @PathVariable("knowledgeBaseId") Long knowledgeBaseId,
            @RequestBody KnowledgeBaseAddDocumentsRequest request) {
        Long userId = UserHolder.requireUserId();
        return ApiResponse.success(knowledgeBaseService.addDocuments(
                userId,
                knowledgeBaseId,
                request == null ? List.of() : request.getDocumentIds()
        ));
    }

    @DeleteMapping("/{knowledgeBaseId}/documents/{documentId}")
    public ApiResponse<KnowledgeBaseDocumentMutationResponse> removeDocument(
            @PathVariable("knowledgeBaseId") Long knowledgeBaseId,
            @PathVariable("documentId") Long documentId) {
        Long userId = UserHolder.requireUserId();
        return ApiResponse.success(knowledgeBaseService.removeDocument(userId, knowledgeBaseId, documentId));
    }
}
