package com.docpilot.backend.ai.agent.tool.spec;

public record ToolExecutionContext(Long userId,
                                   Long documentId,
                                   String sessionId,
                                   String traceId,
                                   Integer indexVersion,
                                   Integer topK) {

    public ToolExecutionContext {
        sessionId = normalize(sessionId);
        traceId = normalize(traceId);
    }

    public boolean hasDocumentScope() {
        return userId != null && documentId != null;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.trim();
    }
}
