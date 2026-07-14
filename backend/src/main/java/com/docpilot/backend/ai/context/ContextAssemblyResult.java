package com.docpilot.backend.ai.context;

import com.docpilot.backend.ai.rag.KnowledgeBaseRagEvidenceCitation;

import java.util.List;

public record ContextAssemblyResult(
        String assembledContext,
        List<PromptMessage> promptMessages,
        List<ContextItem> usedItems,
        ContextTrace trace,
        boolean ragTriggered,
        boolean ragRequired,
        boolean modelCallSkipped,
        String fallbackAnswer,
        List<KnowledgeBaseRagEvidenceCitation> citations
) {

    public ContextAssemblyResult {
        assembledContext = assembledContext == null ? "" : assembledContext.trim();
        promptMessages = promptMessages == null ? List.of() : List.copyOf(promptMessages);
        usedItems = usedItems == null ? List.of() : List.copyOf(usedItems);
        fallbackAnswer = fallbackAnswer == null ? "" : fallbackAnswer.trim();
        citations = citations == null ? List.of() : List.copyOf(citations);
    }
}
