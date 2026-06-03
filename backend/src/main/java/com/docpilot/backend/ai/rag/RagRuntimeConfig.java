package com.docpilot.backend.ai.rag;

import com.docpilot.backend.ai.rag.vector.VectorStoreClient;
import com.docpilot.backend.ai.rag.vector.VectorStoreClientFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RagRuntimeConfig {

    @Bean
    public EmbeddingProvider ragEmbeddingProvider(EmbeddingProviderFactory factory,
                                                  RagEmbeddingProperties properties) {
        return factory.create(properties);
    }

    @Bean
    public VectorStoreClient ragVectorStoreClient(VectorStoreClientFactory factory,
                                                  RagVectorStoreProperties properties) {
        return factory.create(properties);
    }
}
