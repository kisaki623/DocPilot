package com.docpilot.backend.ai.controller;

import com.docpilot.backend.ai.dto.KnowledgeBaseRagQaRequest;
import com.docpilot.backend.ai.dto.KnowledgeBaseRagRetrieveRequest;
import com.docpilot.backend.ai.rag.KnowledgeBaseRagQaQuery;
import com.docpilot.backend.ai.rag.KnowledgeBaseRagRetrievalQuery;
import com.docpilot.backend.ai.service.KnowledgeBaseRagQaService;
import com.docpilot.backend.ai.service.KnowledgeBaseRagRetrievalService;
import com.docpilot.backend.ai.vo.KnowledgeBaseRagQaResponse;
import com.docpilot.backend.ai.vo.KnowledgeBaseRagRetrievalResponse;
import com.docpilot.backend.common.api.ApiResponse;
import com.docpilot.backend.common.context.UserHolder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/knowledge-bases")
public class KnowledgeBaseRagController {

    private final KnowledgeBaseRagRetrievalService retrievalService;
    private final KnowledgeBaseRagQaService qaService;

    public KnowledgeBaseRagController(KnowledgeBaseRagRetrievalService retrievalService,
                                      KnowledgeBaseRagQaService qaService) {
        this.retrievalService = retrievalService;
        this.qaService = qaService;
    }

    @PostMapping("/{knowledgeBaseId}/rag/retrieve")
    public ApiResponse<KnowledgeBaseRagRetrievalResponse> retrieve(
            @PathVariable("knowledgeBaseId") Long knowledgeBaseId,
            @RequestBody KnowledgeBaseRagRetrieveRequest request) {
        Long userId = UserHolder.requireUserId();
        return ApiResponse.success(KnowledgeBaseRagRetrievalResponse.from(retrievalService.retrieve(
                new KnowledgeBaseRagRetrievalQuery(
                        userId,
                        knowledgeBaseId,
                        request == null ? null : request.getQuery(),
                        request == null ? null : request.getTopK(),
                        request == null ? null : request.getIndexVersion(),
                        ""
                )
        )));
    }

    @PostMapping("/{knowledgeBaseId}/qa/rag")
    public ApiResponse<KnowledgeBaseRagQaResponse> qa(
            @PathVariable("knowledgeBaseId") Long knowledgeBaseId,
            @RequestBody KnowledgeBaseRagQaRequest request) {
        Long userId = UserHolder.requireUserId();
        return ApiResponse.success(KnowledgeBaseRagQaResponse.from(qaService.answer(
                new KnowledgeBaseRagQaQuery(
                        userId,
                        knowledgeBaseId,
                        request == null ? null : request.getQuestion(),
                        request == null ? null : request.getTopK(),
                        request == null ? null : request.getIndexVersion(),
                        request == null ? null : request.getSessionId()
                )
        )));
    }
}
