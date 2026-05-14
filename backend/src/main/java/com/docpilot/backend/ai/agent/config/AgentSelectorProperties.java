package com.docpilot.backend.ai.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@ConfigurationProperties(prefix = "app.agent.selector")
public class AgentSelectorProperties {

    public static final String MODE_KEYWORD = "keyword";
    public static final String MODE_SHADOW_LLM = "shadow_llm";
    private static final Set<String> ALLOWED_MODES = Set.of(MODE_KEYWORD, MODE_SHADOW_LLM);

    private String mode = MODE_KEYWORD;
    private boolean shadowEnabled = false;
    private boolean realShadowEnabled = false;
    private boolean realShadowRecordMetrics = false;
    private boolean realShadowFailOpen = true;

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        String normalizedMode = normalizeMode(mode);
        if (!ALLOWED_MODES.contains(normalizedMode)) {
            throw new IllegalArgumentException("Unsupported app.agent.selector.mode='" + mode
                    + "'. Allowed values: keyword, shadow_llm.");
        }
        this.mode = normalizedMode;
    }

    public boolean isShadowEnabled() {
        return shadowEnabled;
    }

    public void setShadowEnabled(boolean shadowEnabled) {
        this.shadowEnabled = shadowEnabled;
    }

    public boolean isRealShadowEnabled() {
        return realShadowEnabled;
    }

    public void setRealShadowEnabled(boolean realShadowEnabled) {
        this.realShadowEnabled = realShadowEnabled;
    }

    public boolean isRealShadowRecordMetrics() {
        return realShadowRecordMetrics;
    }

    public void setRealShadowRecordMetrics(boolean realShadowRecordMetrics) {
        this.realShadowRecordMetrics = realShadowRecordMetrics;
    }

    public boolean isRealShadowFailOpen() {
        return realShadowFailOpen;
    }

    public void setRealShadowFailOpen(boolean realShadowFailOpen) {
        this.realShadowFailOpen = realShadowFailOpen;
    }

    public boolean isShadowLlmMode() {
        return MODE_SHADOW_LLM.equals(mode);
    }

    private String normalizeMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return MODE_KEYWORD;
        }
        return mode.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
