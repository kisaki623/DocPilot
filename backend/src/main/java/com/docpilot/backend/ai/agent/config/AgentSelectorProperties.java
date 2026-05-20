package com.docpilot.backend.ai.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@ConfigurationProperties(prefix = "app.agent.selector")
public class AgentSelectorProperties {

    public static final String MODE_KEYWORD = "keyword";
    public static final String MODE_SHADOW_LLM = "shadow_llm";
    public static final String MODE_LLM_EXECUTE = "llm_execute";
    private static final Set<String> ALLOWED_MODES = Set.of(MODE_KEYWORD, MODE_SHADOW_LLM, MODE_LLM_EXECUTE);
    public static final String PROVIDER_DISABLED = "disabled";
    public static final String PROVIDER_FAKE = "fake";
    public static final String PROVIDER_OPENAI_COMPATIBLE = "openai_compatible";
    private static final Set<String> ALLOWED_PROVIDERS = Set.of(
            PROVIDER_DISABLED,
            PROVIDER_FAKE,
            PROVIDER_OPENAI_COMPATIBLE
    );

    private String mode = MODE_KEYWORD;
    private boolean shadowEnabled = false;
    private boolean realShadowEnabled = false;
    private boolean realShadowRecordMetrics = false;
    private boolean realShadowFailOpen = true;
    private String llmProvider = PROVIDER_DISABLED;
    private String llmModel = "";
    private String llmBaseUrl = "";
    private String llmApiKey = "";
    private int llmRequestTimeoutMs = 15000;
    private int llmMaxTokens = 256;
    private double llmTemperature = 0.0d;

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        String normalizedMode = normalizeMode(mode);
        if (!ALLOWED_MODES.contains(normalizedMode)) {
            throw new IllegalArgumentException("Unsupported app.agent.selector.mode='" + mode
                    + "'. Allowed values: keyword, shadow_llm, llm_execute.");
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

    public String getLlmProvider() {
        return llmProvider;
    }

    public void setLlmProvider(String llmProvider) {
        String normalizedProvider = normalizeProvider(llmProvider);
        if (!ALLOWED_PROVIDERS.contains(normalizedProvider)) {
            throw new IllegalArgumentException("Unsupported app.agent.selector.llm-provider='" + llmProvider
                    + "'. Allowed values: disabled, fake, openai_compatible.");
        }
        this.llmProvider = normalizedProvider;
    }

    public String getLlmModel() {
        return llmModel;
    }

    public void setLlmModel(String llmModel) {
        this.llmModel = llmModel == null ? "" : llmModel.trim();
    }

    public String getLlmBaseUrl() {
        return llmBaseUrl;
    }

    public void setLlmBaseUrl(String llmBaseUrl) {
        this.llmBaseUrl = llmBaseUrl == null ? "" : llmBaseUrl.trim();
    }

    public String getLlmApiKey() {
        return llmApiKey;
    }

    public void setLlmApiKey(String llmApiKey) {
        this.llmApiKey = llmApiKey == null ? "" : llmApiKey.trim();
    }

    public int getLlmRequestTimeoutMs() {
        return llmRequestTimeoutMs;
    }

    public void setLlmRequestTimeoutMs(int llmRequestTimeoutMs) {
        if (llmRequestTimeoutMs <= 0) {
            throw new IllegalArgumentException("app.agent.selector.llm-request-timeout-ms must be positive.");
        }
        this.llmRequestTimeoutMs = llmRequestTimeoutMs;
    }

    public int getLlmMaxTokens() {
        return llmMaxTokens;
    }

    public void setLlmMaxTokens(int llmMaxTokens) {
        if (llmMaxTokens <= 0) {
            throw new IllegalArgumentException("app.agent.selector.llm-max-tokens must be positive.");
        }
        this.llmMaxTokens = llmMaxTokens;
    }

    public double getLlmTemperature() {
        return llmTemperature;
    }

    public void setLlmTemperature(double llmTemperature) {
        if (llmTemperature < 0.0d || llmTemperature > 2.0d) {
            throw new IllegalArgumentException("app.agent.selector.llm-temperature must be between 0.0 and 2.0.");
        }
        this.llmTemperature = llmTemperature;
    }

    public boolean isShadowLlmMode() {
        return MODE_SHADOW_LLM.equals(mode);
    }

    public boolean isLlmExecuteMode() {
        return MODE_LLM_EXECUTE.equals(mode);
    }

    private String normalizeMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return MODE_KEYWORD;
        }
        String normalized = mode.trim().toLowerCase(java.util.Locale.ROOT)
                .replace("-", "_");
        if ("real_llm_execute".equals(normalized)) {
            return MODE_LLM_EXECUTE;
        }
        return normalized;
    }

    private String normalizeProvider(String provider) {
        if (provider == null || provider.isBlank()) {
            return PROVIDER_DISABLED;
        }
        String normalized = provider.trim().toLowerCase(java.util.Locale.ROOT)
                .replace("-", "_");
        if ("openaicompatible".equals(normalized)
                || "deepseek".equals(normalized)
                || "siliconflow".equals(normalized)) {
            return PROVIDER_OPENAI_COMPATIBLE;
        }
        return normalized;
    }
}
