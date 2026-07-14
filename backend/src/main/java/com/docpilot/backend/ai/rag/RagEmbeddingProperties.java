package com.docpilot.backend.ai.rag;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

@Component
@ConfigurationProperties(prefix = "app.rag.embedding")
public class RagEmbeddingProperties {

    public static final String PROVIDER_DISABLED = "disabled";
    public static final String PROVIDER_FAKE = "fake";
    public static final String PROVIDER_MOCK = "mock";
    public static final String PROVIDER_OPENAI_COMPATIBLE = "openai_compatible";
    private static final Set<String> ALLOWED_PROVIDERS = Set.of(
            PROVIDER_DISABLED,
            PROVIDER_FAKE,
            PROVIDER_MOCK,
            PROVIDER_OPENAI_COMPATIBLE
    );

    private boolean enabled = true;
    private String provider = PROVIDER_FAKE;
    private String baseUrl = "";
    private String model = "";
    private String apiKey = "";
    private int connectTimeoutMs = 5000;
    private int requestTimeoutMs = 30000;
    private int dimension = 32;

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
        String normalizedProvider = normalizeProvider(provider);
        if (!ALLOWED_PROVIDERS.contains(normalizedProvider)) {
            throw new IllegalArgumentException("Unsupported app.rag.embedding.provider='" + provider
                    + "'. Allowed values: disabled, fake, mock, openai_compatible.");
        }
        this.provider = normalizedProvider;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model == null ? "" : model.trim();
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        if (connectTimeoutMs <= 0) {
            throw new IllegalArgumentException("app.rag.embedding.connect-timeout-ms must be positive.");
        }
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getRequestTimeoutMs() {
        return requestTimeoutMs;
    }

    public void setRequestTimeoutMs(int requestTimeoutMs) {
        if (requestTimeoutMs <= 0) {
            throw new IllegalArgumentException("app.rag.embedding.request-timeout-ms must be positive.");
        }
        this.requestTimeoutMs = requestTimeoutMs;
    }

    public int getDimension() {
        return dimension;
    }

    public void setDimension(int dimension) {
        if (dimension <= 0) {
            throw new IllegalArgumentException("app.rag.embedding.dimension must be positive.");
        }
        this.dimension = dimension;
    }

    public boolean isFakeProvider() {
        return PROVIDER_FAKE.equals(provider) || PROVIDER_MOCK.equals(provider);
    }

    public boolean isDisabledProvider() {
        return PROVIDER_DISABLED.equals(provider) || !enabled;
    }

    public boolean isOpenAiCompatibleProvider() {
        return PROVIDER_OPENAI_COMPATIBLE.equals(provider);
    }

    private String normalizeProvider(String provider) {
        if (provider == null || provider.isBlank()) {
            return PROVIDER_FAKE;
        }
        String normalized = provider.trim().toLowerCase(Locale.ROOT).replace("-", "_");
        if ("openaicompatible".equals(normalized)) {
            return PROVIDER_OPENAI_COMPATIBLE;
        }
        return normalized;
    }

    public EmbeddingProperties toEmbeddingProperties() {
        EmbeddingProperties properties = new EmbeddingProperties();
        properties.setEnabled(enabled);
        if (isOpenAiCompatibleProvider()) {
            properties.setProvider(EmbeddingProperties.PROVIDER_OPENAI_COMPATIBLE);
        } else if (PROVIDER_DISABLED.equals(provider)) {
            properties.setProvider(EmbeddingProperties.PROVIDER_DISABLED);
        } else {
            properties.setProvider(EmbeddingProperties.PROVIDER_MOCK);
        }
        properties.setBaseUrl(baseUrl);
        properties.setModel(model);
        properties.setApiKey(apiKey);
        properties.setConnectTimeoutMs(connectTimeoutMs);
        properties.setRequestTimeoutMs(requestTimeoutMs);
        properties.setDimension(dimension);
        return properties;
    }
}
