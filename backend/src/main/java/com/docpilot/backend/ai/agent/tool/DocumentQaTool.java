package com.docpilot.backend.ai.agent.tool;

import com.docpilot.backend.ai.service.DocumentQaService;
import com.docpilot.backend.ai.vo.DocumentQaResponse;
import com.docpilot.backend.common.util.ValidationUtils;
import org.springframework.stereotype.Component;

@Component
public class DocumentQaTool implements AgentTool<DocumentQaTool.QaInput, DocumentQaResponse> {

    private final DocumentQaService documentQaService;

    public DocumentQaTool(DocumentQaService documentQaService) {
        this.documentQaService = documentQaService;
    }

    @Override
    public String getToolName() {
        return "document_qa_tool";
    }

    @Override
    public DocumentQaResponse execute(QaInput input) {
        ValidationUtils.requireNonNull(input, "input");
        ValidationUtils.requireNonNull(input.userId(), "userId");
        ValidationUtils.requireNonNull(input.documentId(), "documentId");
        ValidationUtils.requireNonBlank(input.task(), "task");
        return documentQaService.answer(input.userId(), input.documentId(), input.task(), input.sessionId());
    }

    public record QaInput(Long userId, Long documentId, String task, String sessionId) {
    }
}
