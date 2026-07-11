package com.docpilot.backend.ai.rag.rerank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.rag.rerank")
public class RerankProperties {

    public static final String PROVIDER_DISABLED = "disabled";
    public static final String PROVIDER_COHERE = "cohere";
    public static final String PROVIDER_OPENAI_COMPATIBLE = "openai_compatible";
    public static final String PROVIDER_ALIYUN_BAILIAN = "aliyun_bailian";

    private boolean enabled = false;
    private String provider = PROVIDER_DISABLED; // disabled, cohere, openai_compatible, aliyun_bailian
    private String baseUrl = "";
    private String apiKey = "";
    private String model = "";
    private int connectTimeoutMs = 5000;
    private int requestTimeoutMs = 30000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        if (provider == null) {
            this.provider = PROVIDER_DISABLED;
            return;
        }
        String normalized = provider.trim().toLowerCase().replace('-', '_');
        if ("bailian".equals(normalized) || "dashscope".equals(normalized) || "aliyun".equals(normalized)) {
            this.provider = PROVIDER_ALIYUN_BAILIAN;
        } else {
            this.provider = normalized;
        }
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model == null ? "" : model.trim();
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        if (connectTimeoutMs <= 0) {
            throw new IllegalArgumentException("connect-timeout-ms must be positive");
        }
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getRequestTimeoutMs() {
        return requestTimeoutMs;
    }

    public void setRequestTimeoutMs(int requestTimeoutMs) {
        if (requestTimeoutMs <= 0) {
            throw new IllegalArgumentException("request-timeout-ms must be positive");
        }
        this.requestTimeoutMs = requestTimeoutMs;
    }

    public boolean isExternalProviderConfigured() {
        if (!enabled) {
            return false;
        }
        if (PROVIDER_COHERE.equals(provider)) {
            return !apiKey.isBlank() && !model.isBlank();
        }
        if (PROVIDER_OPENAI_COMPATIBLE.equals(provider)) {
            return !baseUrl.isBlank() && !apiKey.isBlank() && !model.isBlank();
        }
        if (PROVIDER_ALIYUN_BAILIAN.equals(provider)) {
            return !baseUrl.isBlank() && !apiKey.isBlank() && !model.isBlank();
        }
        return false;
    }
}
