package com.docpilot.backend.ai.rag.vector;

import com.docpilot.backend.ai.rag.RagVectorStoreProperties;
import com.docpilot.backend.ai.rag.vector.inmemory.InMemoryVectorStoreClient;
import com.docpilot.backend.ai.rag.vector.qdrant.QdrantVectorStoreClient;
import org.springframework.stereotype.Component;

@Component
public class VectorStoreClientFactory {

    public VectorStoreClient create(RagVectorStoreProperties properties) {
        RagVectorStoreProperties resolvedProperties = properties == null ? new RagVectorStoreProperties() : properties;
        if (resolvedProperties.isQdrantDisabledProvider()) {
            return new DisabledVectorStoreClient();
        }
        if (resolvedProperties.isQdrantProvider()) {
            return new QdrantVectorStoreClient(resolvedProperties.getQdrant());
        }
        return new InMemoryVectorStoreClient();
    }
}
