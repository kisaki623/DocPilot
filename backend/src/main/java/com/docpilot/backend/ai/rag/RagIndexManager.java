package com.docpilot.backend.ai.rag;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RagIndexManager {

    public static final String VECTOR_STORE_IN_MEMORY = "in_memory";

    private final Map<RagIndexKey, RagIndexState> states = new ConcurrentHashMap<>();
    private final Clock clock;

    public RagIndexManager() {
        this(Clock.systemUTC());
    }

    RagIndexManager(Clock clock) {
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    public synchronized RagIndexDecision decide(Long documentId,
                                                String documentVersion,
                                                String documentText,
                                                String embeddingProvider,
                                                String vectorStoreType) {
        RagIndexKey key = RagIndexKey.of(documentId, documentVersion);
        String contentHash = sha256(documentText == null ? "" : documentText);
        String safeEmbeddingProvider = safeText(embeddingProvider);
        String safeVectorStoreType = safeText(vectorStoreType);
        RagIndexState existing = states.get(key);
        if (existing != null
                && contentHash.equals(existing.contentHash())
                && safeEmbeddingProvider.equals(existing.embeddingProvider())
                && safeVectorStoreType.equals(existing.vectorStoreType())) {
            return new RagIndexDecision(key, contentHash, true, existing.asReused());
        }
        return new RagIndexDecision(key, contentHash, false, existing);
    }

    public synchronized RagIndexState recordIndexed(RagIndexKey key,
                                                    int chunkCount,
                                                    String embeddingProvider,
                                                    String vectorStoreType,
                                                    String contentHash) {
        states.keySet().removeIf(existingKey -> key.documentId().equals(existingKey.documentId()));
        RagIndexState state = new RagIndexState(
                key,
                Instant.now(clock),
                chunkCount,
                embeddingProvider,
                vectorStoreType,
                contentHash,
                false
        );
        states.put(key, state);
        return state;
    }

    public Optional<RagIndexState> getState(Long documentId, String documentVersion) {
        return Optional.ofNullable(states.get(RagIndexKey.of(documentId, documentVersion)));
    }

    public synchronized void clear() {
        states.clear();
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

    private String safeText(String text) {
        return text == null ? "" : text.trim();
    }

    public record RagIndexDecision(
            RagIndexKey key,
            String contentHash,
            boolean indexReused,
            RagIndexState state
    ) {
    }
}
