package com.docpilot.backend.ai.rag;

import java.util.List;

public interface ChunkingService {

    List<DocumentChunkCandidate> chunk(Long documentId, Long userId, String text);

    List<DocumentChunkCandidate> chunk(Long documentId, Long userId, String text, ChunkingOptions options);
}
