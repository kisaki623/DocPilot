package com.docpilot.backend.ai.rag;

public record RagPrompt(
        String systemPrompt,
        String evidenceContext,
        String userPrompt,
        boolean noEvidence
) {

    public RagPrompt {
        systemPrompt = systemPrompt == null ? "" : systemPrompt.trim();
        evidenceContext = evidenceContext == null ? "" : evidenceContext.trim();
        userPrompt = userPrompt == null ? "" : userPrompt.trim();
    }
}
