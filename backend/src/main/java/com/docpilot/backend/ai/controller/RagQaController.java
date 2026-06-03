package com.docpilot.backend.ai.controller;

import com.docpilot.backend.ai.dto.RagQaRequest;
import com.docpilot.backend.ai.dto.RagRetrieveRequest;
import com.docpilot.backend.ai.rag.RagQaQuery;
import com.docpilot.backend.ai.rag.RagRetrievalQuery;
import com.docpilot.backend.ai.service.RagDocumentRetrievalService;
import com.docpilot.backend.ai.service.RagQaService;
import com.docpilot.backend.ai.vo.RagQaResponse;
import com.docpilot.backend.ai.vo.RagRetrievalResponse;
import com.docpilot.backend.common.api.ApiResponse;
import com.docpilot.backend.common.context.UserHolder;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api")
public class RagQaController {

    private final RagDocumentRetrievalService retrievalService;
    private final RagQaService ragQaService;

    public RagQaController(RagDocumentRetrievalService retrievalService,
                           RagQaService ragQaService) {
        this.retrievalService = retrievalService;
        this.ragQaService = ragQaService;
    }

    @PostMapping("/rag/retrieve")
    public ApiResponse<RagRetrievalResponse> retrieve(@RequestBody RagRetrieveRequest request) {
        Long userId = UserHolder.requireUserId();
        return ApiResponse.success(RagRetrievalResponse.from(retrievalService.retrieve(new RagRetrievalQuery(
                userId,
                request.getDocumentId(),
                request.getQuery(),
                request.getTopK(),
                request.getIndexVersion(),
                ""
        ))));
    }

    @PostMapping("/documents/{documentId}/qa/rag")
    public ApiResponse<RagQaResponse> qa(@PathVariable("documentId") Long documentId,
                                         @RequestBody RagQaRequest request) {
        Long userId = UserHolder.requireUserId();
        return ApiResponse.success(RagQaResponse.from(ragQaService.answer(new RagQaQuery(
                userId,
                documentId,
                request.getQuestion(),
                request.getTopK(),
                request.getIndexVersion(),
                request.getSessionId()
        ))));
    }

    @PostMapping(value = "/documents/{documentId}/qa/rag/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter qaStream(@PathVariable("documentId") Long documentId,
                               @RequestBody RagQaRequest request) {
        Long userId = UserHolder.requireUserId();
        return ragQaService.streamAnswer(new RagQaQuery(
                userId,
                documentId,
                request.getQuestion(),
                request.getTopK(),
                request.getIndexVersion(),
                request.getSessionId()
        ));
    }
}
