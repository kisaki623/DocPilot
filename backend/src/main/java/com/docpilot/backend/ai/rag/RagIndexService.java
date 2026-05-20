package com.docpilot.backend.ai.rag;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RagIndexService {

    public static final int DEFAULT_CHUNK_SIZE = 600;
    public static final int DEFAULT_CHUNK_OVERLAP = 120;

    private final EmbeddingModel embeddingModel;
    private final VectorStore vectorStore;
    private final RagIndexManager indexManager;
    private final String embeddingProvider;
    private final String vectorStoreType;
    private final int chunkSize;
    private final int chunkOverlap;

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
        if (embeddingModel == null) {
            throw new IllegalArgumentException("embeddingModel must not be null");
        }
        if (vectorStore == null) {
            throw new IllegalArgumentException("vectorStore must not be null");
        }
        if (indexManager == null) {
            throw new IllegalArgumentException("indexManager must not be null");
        }
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunkSize must be positive");
        }
        if (chunkOverlap < 0 || chunkOverlap >= chunkSize) {
            throw new IllegalArgumentException("chunkOverlap must be non-negative and smaller than chunkSize");
        }
        this.embeddingModel = embeddingModel;
        this.vectorStore = vectorStore;
        this.indexManager = indexManager;
        this.embeddingProvider = safeText(embeddingProvider);
        this.vectorStoreType = safeText(vectorStoreType);
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
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
            return new RagIndexResult(List.of(), decision.state().chunkCount(), decision.state());
        }

        List<DocumentChunk> chunks = splitDocument(documentId, documentText);
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
        return new RagIndexResult(chunks, chunks.size(), state);
    }

    public List<DocumentChunk> splitDocument(Long documentId, String documentText) {
        if (documentId == null) {
            throw new IllegalArgumentException("documentId must not be null");
        }
        if (documentText == null || documentText.isBlank()) {
            return List.of();
        }

        String normalizedText = normalizeDocumentText(documentText);
        List<DocumentChunk> chunks = new ArrayList<>();
        int start = 0;
        int chunkIndex = 0;
        while (start < normalizedText.length()) {
            int end = Math.min(start + chunkSize, normalizedText.length());
            String chunkText = normalizedText.substring(start, end);
            if (!chunkText.isBlank()) {
                chunks.add(new DocumentChunk(documentId, chunkIndex++, chunkText, metadata(start, end, chunkText)));
            }
            if (end == normalizedText.length()) {
                break;
            }
            start = Math.max(end - chunkOverlap, start + 1);
        }
        return chunks;
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

    private Map<String, String> metadata(int charStart, int charEnd, String text) {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("charStart", String.valueOf(charStart));
        metadata.put("charEnd", String.valueOf(charEnd));
        metadata.put("contentHash", sha256(text));
        metadata.put("chunkVersion", "fake-rag-v1");
        return metadata;
    }

    private String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    public record RagIndexResult(List<DocumentChunk> chunks, int chunkCount, RagIndexState state) {

        public RagIndexResult {
            chunks = chunks == null ? List.of() : List.copyOf(chunks);
            chunkCount = Math.max(0, chunkCount);
            if (state == null) {
                throw new IllegalArgumentException("state must not be null");
            }
        }
    }
}
