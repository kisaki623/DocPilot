package com.docpilot.backend.ai.rag;

import java.util.List;

public class RagIndexService {

    public static final int DEFAULT_CHUNK_SIZE = 600;
    public static final int DEFAULT_CHUNK_OVERLAP = 120;

    private final EmbeddingModel embeddingModel;
    private final VectorStore vectorStore;
    private final RagIndexManager indexManager;
    private final String embeddingProvider;
    private final String vectorStoreType;
    private final RagChunkingPolicy chunkingPolicy;
    private final RagChunker chunker;

    public RagIndexService(EmbeddingModel embeddingModel, VectorStore vectorStore) {
        this(embeddingModel, vectorStore, DEFAULT_CHUNK_SIZE, DEFAULT_CHUNK_OVERLAP);
    }

    public RagIndexService(EmbeddingModelFactory embeddingModelFactory,
                           RagEmbeddingProperties embeddingProperties,
                           VectorStore vectorStore) {
        this(resolveEmbeddingModelFactory(embeddingModelFactory).create(resolveEmbeddingProperties(embeddingProperties)),
                vectorStore,
                new RagIndexManager(),
                resolveEmbeddingProperties(embeddingProperties).getProvider(),
                RagIndexManager.VECTOR_STORE_IN_MEMORY,
                DEFAULT_CHUNK_SIZE, DEFAULT_CHUNK_OVERLAP);
    }

    public RagIndexService(EmbeddingModel embeddingModel, VectorStore vectorStore, int chunkSize, int chunkOverlap) {
        this(embeddingModel, vectorStore, new RagIndexManager(), RagEmbeddingProperties.PROVIDER_FAKE,
                RagIndexManager.VECTOR_STORE_IN_MEMORY, chunkSize, chunkOverlap);
    }

    public RagIndexService(EmbeddingModel embeddingModel,
                           VectorStore vectorStore,
                           RagIndexManager indexManager,
                           String embeddingProvider,
                           String vectorStoreType,
                           int chunkSize,
                           int chunkOverlap) {
        this(embeddingModel, vectorStore, indexManager, embeddingProvider, vectorStoreType,
                RagChunkingPolicy.of(chunkSize, chunkOverlap));
    }

    public RagIndexService(EmbeddingModel embeddingModel,
                           VectorStore vectorStore,
                           RagIndexManager indexManager,
                           String embeddingProvider,
                           String vectorStoreType,
                           RagChunkingPolicy chunkingPolicy) {
        if (embeddingModel == null) {
            throw new IllegalArgumentException("embeddingModel must not be null");
        }
        if (vectorStore == null) {
            throw new IllegalArgumentException("vectorStore must not be null");
        }
        if (indexManager == null) {
            throw new IllegalArgumentException("indexManager must not be null");
        }
        this.embeddingModel = embeddingModel;
        this.vectorStore = vectorStore;
        this.indexManager = indexManager;
        this.embeddingProvider = safeText(embeddingProvider);
        this.vectorStoreType = safeText(vectorStoreType);
        this.chunkingPolicy = chunkingPolicy == null ? RagChunkingPolicy.defaults() : chunkingPolicy;
        this.chunker = new RagChunker(this.chunkingPolicy);
    }

    public List<DocumentChunk> indexDocument(Long documentId, String documentText) {
        return indexDocument(documentId, RagIndexKey.DEFAULT_VERSION, documentText).chunks();
    }

    public RagIndexResult indexDocument(Long documentId, String documentVersion, String documentText) {
        RagIndexManager.RagIndexDecision decision = indexManager.decide(
                documentId,
                documentVersion,
                normalizeDocumentText(documentText),
                embeddingProvider,
                vectorStoreType
        );
        if (decision.indexReused() && decision.state() != null) {
            return new RagIndexResult(List.of(), decision.state().chunkCount(), decision.state(), false);
        }

        RagChunker.RagChunkingResult chunkingResult = chunker.chunk(documentId, documentVersion, documentText);
        List<DocumentChunk> chunks = chunkingResult.chunks();
        vectorStore.deleteDocument(documentId);
        for (DocumentChunk chunk : chunks) {
            vectorStore.add(chunk, embeddingModel.embed(chunk.text()));
        }
        RagIndexState state = indexManager.recordIndexed(
                decision.key(),
                chunks.size(),
                embeddingProvider,
                vectorStoreType,
                decision.contentHash()
        );
        return new RagIndexResult(chunks, chunks.size(), state, chunkingResult.truncated());
    }

    public List<DocumentChunk> splitDocument(Long documentId, String documentText) {
        return chunker.chunk(documentId, RagIndexKey.DEFAULT_VERSION, documentText).chunks();
    }

    private String normalizeDocumentText(String documentText) {
        if (documentText == null) {
            return "";
        }
        return documentText.replace("\r\n", "\n").replace('\r', '\n');
    }

    private String safeText(String text) {
        return text == null ? "" : text.trim();
    }

    private static EmbeddingModelFactory resolveEmbeddingModelFactory(EmbeddingModelFactory embeddingModelFactory) {
        return embeddingModelFactory == null ? new EmbeddingModelFactory() : embeddingModelFactory;
    }

    private static RagEmbeddingProperties resolveEmbeddingProperties(RagEmbeddingProperties embeddingProperties) {
        return embeddingProperties == null ? new RagEmbeddingProperties() : embeddingProperties;
    }

    public record RagIndexResult(List<DocumentChunk> chunks, int chunkCount, RagIndexState state, boolean indexTruncated) {

        public RagIndexResult {
            chunks = chunks == null ? List.of() : List.copyOf(chunks);
            chunkCount = Math.max(0, chunkCount);
            if (state == null) {
                throw new IllegalArgumentException("state must not be null");
            }
        }
    }
}
