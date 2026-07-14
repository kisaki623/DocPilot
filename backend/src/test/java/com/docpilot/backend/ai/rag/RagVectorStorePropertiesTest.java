package com.docpilot.backend.ai.rag;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RagVectorStorePropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(RagVectorStoreProperties.class)
            .withPropertyValues(
                    "app.rag.vector-store.provider=in_memory",
                    "app.rag.vector-store.qdrant.collection=docpilot_rag_demo",
                    "app.rag.vector-store.qdrant.endpoint=",
                    "app.rag.vector-store.qdrant.api-key=",
                    "app.rag.vector-store.qdrant.connect-timeout-ms=5000",
                    "app.rag.vector-store.qdrant.request-timeout-ms=30000"
            );

    @Test
    void shouldUseInMemoryProviderByDefault() {
        contextRunner.run(context -> {
            RagVectorStoreProperties properties = context.getBean(RagVectorStoreProperties.class);

            assertThat(properties.getProvider()).isEqualTo(RagVectorStoreProperties.PROVIDER_IN_MEMORY);
            assertThat(properties.isInMemoryProvider()).isTrue();
            assertThat(properties.isQdrantDisabledProvider()).isFalse();
            assertThat(properties.isQdrantProvider()).isFalse();
            assertThat(properties.getQdrant().getCollection()).isEqualTo("docpilot_rag_demo");
            assertThat(properties.getQdrant().getEndpoint()).isEmpty();
            assertThat(properties.getQdrant().getHost()).isEmpty();
            assertThat(properties.getQdrant().getPort()).isEqualTo(6333);
            assertThat(properties.getQdrant().getApiKey()).isEmpty();
            assertThat(properties.getQdrant().getDimension()).isEqualTo(1536);
            assertThat(properties.getQdrant().getDistance()).isEqualTo("Cosine");
            assertThat(properties.getQdrant().isCollectionInitEnabled()).isFalse();
            assertThat(properties.getQdrant().getConnectTimeoutMs()).isEqualTo(5000);
            assertThat(properties.getQdrant().getRequestTimeoutMs()).isEqualTo(30000);
        });
    }

    @Test
    void shouldBindQdrantDisabledProviderWithoutEndpoint() {
        contextRunner.withPropertyValues("app.rag.vector-store.provider=qdrant_disabled")
                .run(context -> {
                    RagVectorStoreProperties properties = context.getBean(RagVectorStoreProperties.class);

                    assertThat(properties.getProvider()).isEqualTo(RagVectorStoreProperties.PROVIDER_QDRANT_DISABLED);
                    assertThat(properties.isQdrantDisabledProvider()).isTrue();
                    assertThat(properties.getQdrant().getEndpoint()).isEmpty();
                });
    }

    @Test
    void shouldBindExplicitQdrantProvider() {
        contextRunner.withPropertyValues(
                        "app.rag.vector-store.provider=qdrant",
                        "app.rag.vector-store.qdrant.endpoint=http://127.0.0.1:6333",
                        "app.rag.vector-store.qdrant.host=qdrant",
                        "app.rag.vector-store.qdrant.port=6334",
                        "app.rag.vector-store.qdrant.api-key=test-key",
                        "app.rag.vector-store.qdrant.collection=docpilot_test",
                        "app.rag.vector-store.qdrant.dimension=768",
                        "app.rag.vector-store.qdrant.distance=Dot",
                        "app.rag.vector-store.qdrant.collection-init-enabled=true",
                        "app.rag.vector-store.qdrant.connect-timeout-ms=1234",
                        "app.rag.vector-store.qdrant.request-timeout-ms=5678"
                )
                .run(context -> {
                    RagVectorStoreProperties properties = context.getBean(RagVectorStoreProperties.class);

                    assertThat(properties.getProvider()).isEqualTo(RagVectorStoreProperties.PROVIDER_QDRANT);
                    assertThat(properties.isQdrantProvider()).isTrue();
                    assertThat(properties.getQdrant().getEndpoint()).isEqualTo("http://127.0.0.1:6333");
                    assertThat(properties.getQdrant().getHost()).isEqualTo("qdrant");
                    assertThat(properties.getQdrant().getPort()).isEqualTo(6334);
                    assertThat(properties.getQdrant().getApiKey()).isEqualTo("test-key");
                    assertThat(properties.getQdrant().getCollection()).isEqualTo("docpilot_test");
                    assertThat(properties.getQdrant().getDimension()).isEqualTo(768);
                    assertThat(properties.getQdrant().getDistance()).isEqualTo("Dot");
                    assertThat(properties.getQdrant().isCollectionInitEnabled()).isTrue();
                    assertThat(properties.getQdrant().getConnectTimeoutMs()).isEqualTo(1234);
                    assertThat(properties.getQdrant().getRequestTimeoutMs()).isEqualTo(5678);
                });
    }

    @Test
    void applicationYamlShouldPreferRecommendedQdrantEnvNamesWithLegacyFallback() throws IOException {
        String yaml = Files.readString(Path.of("src/main/resources/application.yml"), StandardCharsets.UTF_8);

        assertThat(yaml).contains("import: ${SPRING_CONFIG_IMPORT:optional:file:./.env[.properties]}");
        assertThat(yaml).contains("provider: ${RAG_VECTOR_STORE_PROVIDER:${RAG_VECTOR_PROVIDER:${APP_RAG_VECTOR_STORE_PROVIDER:in_memory}}}");
        assertThat(yaml).contains("collection: ${RAG_QDRANT_COLLECTION:${APP_RAG_VECTOR_STORE_QDRANT_COLLECTION:docpilot_rag_demo}}");
        assertThat(yaml).contains("endpoint: ${RAG_QDRANT_ENDPOINT:${APP_RAG_VECTOR_STORE_QDRANT_ENDPOINT:}}");
        assertThat(yaml).contains("host: ${RAG_QDRANT_HOST:${APP_RAG_VECTOR_STORE_QDRANT_HOST:}}");
        assertThat(yaml).contains("port: ${RAG_QDRANT_PORT:${APP_RAG_VECTOR_STORE_QDRANT_PORT:6333}}");
        assertThat(yaml).contains("api-key: ${RAG_QDRANT_API_KEY:${APP_RAG_VECTOR_STORE_QDRANT_API_KEY:}}");
        assertThat(yaml).contains("dimension: ${RAG_QDRANT_DIMENSION:${RAG_VECTOR_DIMENSION:${APP_RAG_VECTOR_STORE_QDRANT_DIMENSION:1536}}}");
        assertThat(yaml).contains("distance: ${RAG_QDRANT_DISTANCE:${APP_RAG_VECTOR_STORE_QDRANT_DISTANCE:Cosine}}");
        assertThat(yaml).contains("collection-init-enabled: ${RAG_QDRANT_COLLECTION_INIT_ENABLED:${APP_RAG_VECTOR_STORE_QDRANT_COLLECTION_INIT_ENABLED:false}}");
        assertThat(yaml).contains("connect-timeout-ms: ${RAG_QDRANT_CONNECT_TIMEOUT_MS:${APP_RAG_VECTOR_STORE_QDRANT_CONNECT_TIMEOUT_MS:5000}}");
        assertThat(yaml).contains("request-timeout-ms: ${RAG_QDRANT_REQUEST_TIMEOUT_MS:${APP_RAG_VECTOR_STORE_QDRANT_REQUEST_TIMEOUT_MS:30000}}");
    }

    @Test
    void localProfileYamlShouldNotOwnEnvFileImport() throws IOException {
        String yaml = Files.readString(Path.of("src/main/resources/application-local.yml"), StandardCharsets.UTF_8);

        assertThat(yaml).doesNotContain("spring.config.import");
        assertThat(yaml).doesNotContain("optional:file:./.env");
    }

    @Test
    void shouldNormalizeProviderAliases() {
        assertProviderAlias("in-memory", RagVectorStoreProperties.PROVIDER_IN_MEMORY);
        assertProviderAlias("inMemory", RagVectorStoreProperties.PROVIDER_IN_MEMORY);
        assertProviderAlias("qdrant-disabled", RagVectorStoreProperties.PROVIDER_QDRANT_DISABLED);
        assertProviderAlias("qdrantDisabled", RagVectorStoreProperties.PROVIDER_QDRANT_DISABLED);
    }

    @Test
    void shouldRejectUnsupportedProvider() {
        contextRunner.withPropertyValues("app.rag.vector-store.provider=definitely_unknown")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage("Unsupported app.rag.vector-store.provider='definitely_unknown'. Allowed values: in_memory, qdrant_disabled, qdrant.");
                });
    }

    @Test
    void shouldRejectInvalidQdrantPortAndDimension() {
        contextRunner.withPropertyValues("app.rag.vector-store.qdrant.port=0")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage("app.rag.vector-store.qdrant.port must be between 1 and 65535.");
                });

        contextRunner.withPropertyValues("app.rag.vector-store.qdrant.dimension=0")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage("app.rag.vector-store.qdrant.dimension must be positive.");
                });
    }

    @Test
    void shouldRejectNonPositiveQdrantTimeouts() {
        contextRunner.withPropertyValues("app.rag.vector-store.qdrant.connect-timeout-ms=0")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage("app.rag.vector-store.qdrant.connect-timeout-ms must be positive.");
                });

        contextRunner.withPropertyValues("app.rag.vector-store.qdrant.request-timeout-ms=0")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage("app.rag.vector-store.qdrant.request-timeout-ms must be positive.");
                });
    }

    private void assertProviderAlias(String provider, String expectedProvider) {
        contextRunner.withPropertyValues("app.rag.vector-store.provider=" + provider)
                .run(context -> {
                    RagVectorStoreProperties properties = context.getBean(RagVectorStoreProperties.class);

                    assertThat(properties.getProvider()).isEqualTo(expectedProvider);
                });
    }
}
