package com.docpilot.backend.ai.rag;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class InMemoryVectorStore implements VectorStore {

    private final List<Entry> entries = new ArrayList<>();

    @Override
    public synchronized void add(DocumentChunk chunk, EmbeddingVector vector) {
        if (chunk == null) {
            throw new IllegalArgumentException("chunk must not be null");
        }
        if (vector == null) {
            throw new IllegalArgumentException("vector must not be null");
        }
        entries.add(new Entry(chunk, vector));
    }

    @Override
    public synchronized List<VectorSearchResult> searchTopK(Long documentId, EmbeddingVector queryVector, int topK) {
        if (documentId == null) {
            throw new IllegalArgumentException("documentId must not be null");
        }
        if (queryVector == null) {
            throw new IllegalArgumentException("queryVector must not be null");
        }
        if (topK <= 0) {
            return List.of();
        }
        return entries.stream()
                .filter(entry -> documentId.equals(entry.chunk().documentId()))
                .map(entry -> new VectorSearchResult(entry.chunk(), cosineSimilarity(queryVector, entry.vector())))
                .sorted(Comparator.comparingDouble(VectorSearchResult::score).reversed()
                        .thenComparing(result -> result.chunk().chunkIndex()))
                .limit(topK)
                .toList();
    }

    @Override
    public synchronized void deleteDocument(Long documentId) {
        if (documentId == null) {
            throw new IllegalArgumentException("documentId must not be null");
        }
        entries.removeIf(entry -> documentId.equals(entry.chunk().documentId()));
    }

    @Override
    public synchronized void clear() {
        entries.clear();
    }

    public synchronized int size() {
        return entries.size();
    }

    private double cosineSimilarity(EmbeddingVector left, EmbeddingVector right) {
        int size = Math.min(left.dimension(), right.dimension());
        double dot = 0.0D;
        double leftNorm = 0.0D;
        double rightNorm = 0.0D;
        for (int i = 0; i < size; i++) {
            double leftValue = left.values().get(i);
            double rightValue = right.values().get(i);
            dot += leftValue * rightValue;
            leftNorm += leftValue * leftValue;
            rightNorm += rightValue * rightValue;
        }
        if (leftNorm == 0.0D || rightNorm == 0.0D) {
            return 0.0D;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    private record Entry(DocumentChunk chunk, EmbeddingVector vector) {
    }
}
