package com.docpilot.backend.ai.rag;

import org.springframework.stereotype.Component;

@Component
public class VectorStoreFactory {

    public VectorStore create(RagVectorStoreProperties properties, InMemoryVectorStore inMemoryVectorStore) {
        RagVectorStoreProperties resolvedProperties = properties == null
                ? new RagVectorStoreProperties()
                : properties;
        if (resolvedProperties.isQdrantDisabledProvider()) {
            return new DisabledQdrantVectorStore(resolvedProperties.getQdrant());
        }
        return inMemoryVectorStore == null ? new InMemoryVectorStore() : inMemoryVectorStore;
    }
}
