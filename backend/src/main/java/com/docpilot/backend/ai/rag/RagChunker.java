package com.docpilot.backend.ai.rag;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

public class RagChunker {

    private final RagChunkingPolicy policy;

    public RagChunker() {
        this(RagChunkingPolicy.defaults());
    }

    public RagChunker(RagChunkingPolicy policy) {
        this.policy = policy == null ? RagChunkingPolicy.defaults() : policy;
    }

    public RagChunkingResult chunk(Long documentId, String documentVersion, String documentText) {
        if (documentId == null) {
            throw new IllegalArgumentException("documentId must not be null");
        }
        String normalizedText = normalize(documentText);
        if (normalizedText.isBlank()) {
            return new RagChunkingResult(List.of(), false);
        }

        List<PendingChunk> pendingChunks = new ArrayList<>();
        int start = 0;
        int chunkIndex = 0;
        boolean truncated = false;
        while (start < normalizedText.length()) {
            if (chunkIndex >= policy.maxChunksPerDocument()) {
                truncated = true;
                break;
            }
            int end = Math.min(start + policy.maxChunkChars(), normalizedText.length());
            String chunkText = normalizedText.substring(start, end);
            if (!chunkText.isBlank()) {
                pendingChunks.add(new PendingChunk(chunkIndex++, chunkText, start, end));
            }
            if (end == normalizedText.length()) {
                break;
            }
            start = Math.max(end - policy.overlapChars(), start + 1);
        }

        boolean resultTruncated = truncated;
        List<DocumentChunk> chunks = pendingChunks.stream()
                .map(chunk -> toDocumentChunk(documentId, documentVersion, chunk, resultTruncated))
                .toList();
        return new RagChunkingResult(chunks, resultTruncated);
    }

    private DocumentChunk toDocumentChunk(Long documentId,
                                          String documentVersion,
                                          PendingChunk pendingChunk,
                                          boolean truncated) {
        String contentHash = sha256(pendingChunk.text());
        String resolvedVersion = documentVersion == null || documentVersion.isBlank()
                ? RagIndexKey.DEFAULT_VERSION
                : documentVersion.trim();
        String chunkId = stableChunkId(documentId, resolvedVersion, pendingChunk.chunkIndex(), contentHash);
        RagChunkMetadata metadata = new RagChunkMetadata(
                documentId,
                resolvedVersion,
                pendingChunk.chunkIndex(),
                chunkId,
                contentHash,
                pendingChunk.startOffset(),
                pendingChunk.endOffset(),
                truncated
        );
        return new DocumentChunk(documentId, pendingChunk.chunkIndex(), pendingChunk.text(), metadata.toMap());
    }

    private String stableChunkId(Long documentId, String documentVersion, int chunkIndex, String contentHash) {
        String seed = documentId + "|" + documentVersion + "|" + chunkIndex + "|" + contentHash;
        return "chunk_" + sha256(seed).substring(0, 24);
    }

    private String normalize(String documentText) {
        if (documentText == null) {
            return "";
        }
        return documentText.replace("\r\n", "\n").replace('\r', '\n');
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

    public record RagChunkingResult(List<DocumentChunk> chunks, boolean truncated) {

        public RagChunkingResult {
            chunks = chunks == null ? List.of() : List.copyOf(chunks);
        }
    }

    private record PendingChunk(int chunkIndex, String text, int startOffset, int endOffset) {
    }
}
