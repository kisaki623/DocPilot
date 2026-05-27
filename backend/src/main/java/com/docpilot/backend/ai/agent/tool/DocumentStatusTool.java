package com.docpilot.backend.ai.agent.tool;

import com.docpilot.backend.common.constant.ParseStatusConstants;
import com.docpilot.backend.common.util.ValidationUtils;
import com.docpilot.backend.document.service.DocumentService;
import com.docpilot.backend.document.vo.DocumentDetailResponse;
import org.springframework.stereotype.Component;

@Component
public class DocumentStatusTool implements AgentTool<DocumentStatusTool.StatusInput, DocumentStatusTool.StatusResult> {

    private final DocumentService documentService;

    public DocumentStatusTool(DocumentService documentService) {
        this.documentService = documentService;
    }

    @Override
    public String getToolName() {
        return "document_status_tool";
    }

    @Override
    public StatusResult execute(StatusInput input) {
        ValidationUtils.requireNonNull(input, "input");
        ValidationUtils.requireNonNull(input.userId(), "userId");
        ValidationUtils.requireNonNull(input.documentId(), "documentId");

        DocumentDetailResponse detail = documentService.getDetailById(input.documentId(), input.userId());
        return new StatusResult(
                detail.getDocumentId(),
                detail.getTitle(),
                detail.getParseStatus(),
                ParseStatusConstants.SUCCESS.equals(detail.getParseStatus()),
                detail.getParseStatusDescription(),
                detail.getSummary(),
                detail.getContent()
        );
    }

    public record StatusInput(Long userId, Long documentId) {
    }

    public record StatusResult(Long documentId,
                               String title,
                               String parseStatus,
                               boolean parseReady,
                               String parseStatusDescription,
                               String summary,
                               String content) {
    }
}
