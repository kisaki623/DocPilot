package com.docpilot.backend.ai.rag;

import org.springframework.stereotype.Component;

@Component
public class VectorStoreFactory {

    public VectorStore create(RagVectorStoreProperties properties, VectorStore defaultVectorStore) {
        RagVectorStoreProperties resolvedProperties = properties == null
                ? new RagVectorStoreProperties()
                : properties;
        if (resolvedProperties.isQdrantDisabledProvider()) {
            return new DisabledQdrantVectorStore(resolvedProperties.getQdrant());
        }
        if (resolvedProperties.isQdrantProvider()) {
            return new QdrantVectorStore(resolvedProperties.getQdrant());
        }
        return defaultVectorStore == null ? new InMemoryVectorStore() : defaultVectorStore;
    }
}
