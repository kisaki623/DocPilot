package com.docpilot.backend.ai.controller;

import com.docpilot.backend.ai.dto.DocumentQaRequest;
import com.docpilot.backend.ai.service.DocumentQaService;
import com.docpilot.backend.ai.vo.DocumentQaHistoryItemResponse;
import com.docpilot.backend.ai.vo.DocumentQaResponse;
import com.docpilot.backend.common.api.ApiResponse;
import com.docpilot.backend.common.context.UserHolder;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/ai")
public class DocumentQaController {

    private final DocumentQaService documentQaService;

    public DocumentQaController(DocumentQaService documentQaService) {
        this.documentQaService = documentQaService;
    }

    @PostMapping("/qa")
    public ApiResponse<DocumentQaResponse> qa(@RequestBody DocumentQaRequest request) {
        Long userId = UserHolder.requireUserId();
        return ApiResponse.success(documentQaService.answer(
                userId,
                request.getDocumentId(),
                request.getQuestion(),
                request.getSessionId()
        ));
    }

    @PostMapping(value = "/qa/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter qaStream(@RequestBody DocumentQaRequest request) {
        Long userId = UserHolder.requireUserId();
        return documentQaService.streamAnswer(
                userId,
                request.getDocumentId(),
                request.getQuestion(),
                request.getSessionId()
        );
    }

    @GetMapping("/qa/history")
    public ApiResponse<List<DocumentQaHistoryItemResponse>> qaHistory(@RequestParam("documentId") Long documentId,
                                                                      @RequestParam(value = "limit", required = false) Integer limit) {
        Long userId = UserHolder.requireUserId();
        return ApiResponse.success(documentQaService.listHistory(userId, documentId, limit));
    }
}

