package com.docpilot.backend.ai.rag;

import java.util.List;

public record RagAnswerContext(
        String contextText,
        List<RagCitation> citations
) {

    public RagAnswerContext {
        contextText = contextText == null ? "" : contextText;
        citations = citations == null ? List.of() : List.copyOf(citations);
    }
}
