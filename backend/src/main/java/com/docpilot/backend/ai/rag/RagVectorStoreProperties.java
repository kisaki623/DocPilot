package com.docpilot.backend.ai.rag;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

@Component
@ConfigurationProperties(prefix = "app.rag.vector-store")
public class RagVectorStoreProperties {

    public static final String PROVIDER_IN_MEMORY = "in_memory";
    public static final String PROVIDER_QDRANT_DISABLED = "qdrant_disabled";
    public static final String PROVIDER_QDRANT = "qdrant";
    private static final Set<String> ALLOWED_PROVIDERS = Set.of(
            PROVIDER_IN_MEMORY,
            PROVIDER_QDRANT_DISABLED,
            PROVIDER_QDRANT
    );

    private String provider = PROVIDER_IN_MEMORY;
    private Qdrant qdrant = new Qdrant();

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        String normalizedProvider = normalizeProvider(provider);
        if (!ALLOWED_PROVIDERS.contains(normalizedProvider)) {
            throw new IllegalArgumentException("Unsupported app.rag.vector-store.provider='" + provider
                    + "'. Allowed values: in_memory, qdrant_disabled, qdrant.");
        }
        this.provider = normalizedProvider;
    }

    public Qdrant getQdrant() {
        return qdrant;
    }

    public void setQdrant(Qdrant qdrant) {
        this.qdrant = qdrant == null ? new Qdrant() : qdrant;
    }

    public boolean isInMemoryProvider() {
        return PROVIDER_IN_MEMORY.equals(provider);
    }

    public boolean isQdrantDisabledProvider() {
        return PROVIDER_QDRANT_DISABLED.equals(provider);
    }

    public boolean isQdrantProvider() {
        return PROVIDER_QDRANT.equals(provider);
    }

    private String normalizeProvider(String provider) {
        if (provider == null || provider.isBlank()) {
            return PROVIDER_IN_MEMORY;
        }
        String normalized = provider.trim().toLowerCase(Locale.ROOT).replace("-", "_");
        if ("qdrantdisabled".equals(normalized)) {
            return PROVIDER_QDRANT_DISABLED;
        }
        if ("memory".equals(normalized) || "inmemory".equals(normalized)) {
            return PROVIDER_IN_MEMORY;
        }
        return normalized;
    }

    public static class Qdrant {

        private String collection = "docpilot_rag_demo";
        private String endpoint = "";
        private String host = "";
        private int port = 6333;
        private String apiKey = "";
        private int dimension = 1536;
        private String distance = "Cosine";
        private boolean collectionInitEnabled = false;
        private int connectTimeoutMs = 5000;
        private int requestTimeoutMs = 30000;

        public String getCollection() {
            return collection;
        }

        public void setCollection(String collection) {
            this.collection = collection == null || collection.isBlank()
                    ? "docpilot_rag_demo"
                    : collection.trim();
        }

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint == null ? "" : endpoint.trim();
        }

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host == null ? "" : host.trim();
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            if (port <= 0 || port > 65535) {
                throw new IllegalArgumentException("app.rag.vector-store.qdrant.port must be between 1 and 65535.");
            }
            this.port = port;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey == null ? "" : apiKey.trim();
        }

        public int getDimension() {
            return dimension;
        }

        public void setDimension(int dimension) {
            if (dimension <= 0) {
                throw new IllegalArgumentException("app.rag.vector-store.qdrant.dimension must be positive.");
            }
            this.dimension = dimension;
        }

        public String getDistance() {
            return distance;
        }

        public void setDistance(String distance) {
            this.distance = distance == null || distance.isBlank() ? "Cosine" : distance.trim();
        }

        public boolean isCollectionInitEnabled() {
            return collectionInitEnabled;
        }

        public void setCollectionInitEnabled(boolean collectionInitEnabled) {
            this.collectionInitEnabled = collectionInitEnabled;
        }

        public int getConnectTimeoutMs() {
            return connectTimeoutMs;
        }

        public void setConnectTimeoutMs(int connectTimeoutMs) {
            if (connectTimeoutMs <= 0) {
                throw new IllegalArgumentException("app.rag.vector-store.qdrant.connect-timeout-ms must be positive.");
            }
            this.connectTimeoutMs = connectTimeoutMs;
        }

        public int getRequestTimeoutMs() {
            return requestTimeoutMs;
        }

        public void setRequestTimeoutMs(int requestTimeoutMs) {
            if (requestTimeoutMs <= 0) {
                throw new IllegalArgumentException("app.rag.vector-store.qdrant.request-timeout-ms must be positive.");
            }
            this.requestTimeoutMs = requestTimeoutMs;
        }
    }
}
