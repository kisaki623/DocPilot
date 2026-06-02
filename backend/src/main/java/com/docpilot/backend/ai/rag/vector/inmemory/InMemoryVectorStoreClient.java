package com.docpilot.backend.ai.rag.vector.inmemory;

import com.docpilot.backend.ai.rag.EmbeddingVector;
import com.docpilot.backend.ai.rag.vector.VectorPoint;
import com.docpilot.backend.ai.rag.vector.VectorSearchHit;
import com.docpilot.backend.ai.rag.vector.VectorSearchRequest;
import com.docpilot.backend.ai.rag.vector.VectorSearchResult;
import com.docpilot.backend.ai.rag.vector.VectorStoreClient;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class InMemoryVectorStoreClient implements VectorStoreClient {

    private final Map<String, VectorPoint> points = new LinkedHashMap<>();

    @Override
    public synchronized void upsert(List<VectorPoint> points) {
        if (points == null || points.isEmpty()) {
            throw new IllegalArgumentException("points must not be empty");
        }
        for (VectorPoint point : points) {
            if (point == null) {
                throw new IllegalArgumentException("point must not be null");
            }
            this.points.put(point.id(), point);
        }
    }

    @Override
    public synchronized VectorSearchResult search(VectorSearchRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        List<VectorSearchHit> hits = points.values().stream()
                .filter(point -> matches(request, point))
                .map(point -> hit(point, cosineSimilarity(request.queryVector(), point.vector())))
                .sorted(Comparator.comparingDouble(VectorSearchHit::score).reversed()
                        .thenComparing(VectorSearchHit::chunkIndex)
                        .thenComparing(VectorSearchHit::id))
                .limit(request.topK())
                .toList();
        return new VectorSearchResult(hits, "in_memory", "");
    }

    @Override
    public synchronized void deleteByDocumentId(Long userId, Long documentId, Integer indexVersion) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        if (documentId == null) {
            throw new IllegalArgumentException("documentId must not be null");
        }
        if (indexVersion != null && indexVersion <= 0) {
            throw new IllegalArgumentException("indexVersion must be positive when provided");
        }
        points.values().removeIf(point -> userId.equals(point.userId())
                && documentId.equals(point.documentId())
                && (indexVersion == null || indexVersion.equals(point.indexVersion())));
    }

    public synchronized int size() {
        return points.size();
    }

    private boolean matches(VectorSearchRequest request, VectorPoint point) {
        return request.userId().equals(point.userId())
                && request.documentId().equals(point.documentId())
                && (request.indexVersion() == null || request.indexVersion().equals(point.indexVersion()));
    }

    private VectorSearchHit hit(VectorPoint point, double score) {
        return new VectorSearchHit(
                point.id(),
                score,
                point.userId(),
                point.documentId(),
                point.indexVersion(),
                point.chunkIndex(),
                point.content(),
                point.contentHash(),
                point.payload()
        );
    }

    private double cosineSimilarity(EmbeddingVector left, EmbeddingVector right) {
        if (left.dimension() != right.dimension()) {
            throw new IllegalArgumentException("query vector dimension must match indexed vector dimension");
        }
        double dot = 0.0D;
        double leftNorm = 0.0D;
        double rightNorm = 0.0D;
        for (int i = 0; i < left.values().size(); i++) {
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
}
