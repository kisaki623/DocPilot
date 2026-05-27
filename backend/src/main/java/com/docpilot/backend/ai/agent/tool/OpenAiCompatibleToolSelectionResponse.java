package com.docpilot.backend.ai.agent.tool;

public record OpenAiCompatibleToolSelectionResponse(String rawText,
                                                    String provider,
                                                    String model,
                                                    String finishReason) {

    public OpenAiCompatibleToolSelectionResponse {
        rawText = rawText == null ? "" : rawText;
        provider = provider == null || provider.isBlank() ? "openai_compatible" : provider.trim();
        model = model == null ? "" : model.trim();
        finishReason = finishReason == null ? "" : finishReason.trim();
    }
}
