package com.docpilot.backend.ai.service.impl;

import com.docpilot.backend.ai.entity.DocumentChunkEntity;
import com.docpilot.backend.ai.rag.ChunkingService;
import com.docpilot.backend.ai.rag.DocumentChunkCandidate;
import com.docpilot.backend.ai.rag.EmbeddingProvider;
import com.docpilot.backend.ai.rag.EmbeddingProviderFactory;
import com.docpilot.backend.ai.rag.EmbeddingRequest;
import com.docpilot.backend.ai.rag.EmbeddingResult;
import com.docpilot.backend.ai.rag.EmbeddingVector;
import com.docpilot.backend.ai.rag.RagEmbeddingProperties;
import com.docpilot.backend.ai.rag.RagIndexingRequest;
import com.docpilot.backend.ai.rag.RagIndexingResult;
import com.docpilot.backend.ai.rag.RagIndexingStatus;
import com.docpilot.backend.ai.rag.RagVectorStoreProperties;
import com.docpilot.backend.ai.rag.vector.VectorPoint;
import com.docpilot.backend.ai.rag.vector.VectorStoreClient;
import com.docpilot.backend.ai.rag.vector.VectorStoreClientFactory;
import com.docpilot.backend.ai.service.DocumentChunkService;
import com.docpilot.backend.ai.service.RagIndexingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RagIndexingServiceImpl implements RagIndexingService {

    private static final String REPLACE_SEMANTICS_MESSAGE =
            "T004 MVP uses replace semantics for index/rebuild/retry; incremental indexing is deferred.";

    private final ChunkingService chunkingService;
    private final DocumentChunkService documentChunkService;
    private final EmbeddingProvider embeddingProvider;
    private final VectorStoreClient vectorStoreClient;
    private final RagEmbeddingProperties embeddingProperties;
    private final RagVectorStoreProperties vectorStoreProperties;

    public RagIndexingServiceImpl(ChunkingService chunkingService,
                                  DocumentChunkService documentChunkService,
                                  EmbeddingProviderFactory embeddingProviderFactory,
                                  VectorStoreClientFactory vectorStoreClientFactory,
                                  RagEmbeddingProperties embeddingProperties,
                                  RagVectorStoreProperties vectorStoreProperties) {
        this(
                chunkingService,
                documentChunkService,
                embeddingProviderFactory == null
                        ? new EmbeddingProviderFactory().create(embeddingProperties)
                        : embeddingProviderFactory.create(embeddingProperties),
                vectorStoreClientFactory == null
                        ? new VectorStoreClientFactory().create(vectorStoreProperties)
                        : vectorStoreClientFactory.create(vectorStoreProperties),
                embeddingProperties,
                vectorStoreProperties
        );
    }

    @Autowired
    public RagIndexingServiceImpl(ChunkingService chunkingService,
                                  DocumentChunkService documentChunkService,
                                  EmbeddingProvider embeddingProvider,
                                  VectorStoreClient vectorStoreClient,
                                  RagEmbeddingProperties embeddingProperties,
                                  RagVectorStoreProperties vectorStoreProperties) {
        this.chunkingService = chunkingService;
        this.documentChunkService = documentChunkService;
        this.embeddingProvider = embeddingProvider;
        this.vectorStoreClient = vectorStoreClient;
        this.embeddingProperties = embeddingProperties == null ? new RagEmbeddingProperties() : embeddingProperties;
        this.vectorStoreProperties = vectorStoreProperties == null ? new RagVectorStoreProperties() : vectorStoreProperties;
    }

    @Override
    public RagIndexingResult index(RagIndexingRequest request) {
        return replaceWorkflow(request, "index");
    }

    @Override
    public RagIndexingResult rebuild(RagIndexingRequest request) {
        return replaceWorkflow(request, "rebuild");
    }

    @Override
    public RagIndexingResult retry(RagIndexingRequest request) {
        return replaceWorkflow(request, "retry");
    }

    private RagIndexingResult replaceWorkflow(RagIndexingRequest request, String operation) {
        RagIndexingRequest resolvedRequest = validateAndResolve(request);
        if (resolvedRequest.text().isBlank()) {
            return result(
                    RagIndexingStatus.SKIPPED_EMPTY_TEXT,
                    resolvedRequest,
                    0,
                    0,
                    operation + " skipped because text is blank. " + REPLACE_SEMANTICS_MESSAGE
            );
        }

        List<DocumentChunkCandidate> candidates = chunkingService.chunk(
                resolvedRequest.documentId(),
                resolvedRequest.userId(),
                resolvedRequest.text()
        );
        if (candidates.isEmpty()) {
            return result(
                    RagIndexingStatus.SKIPPED_EMPTY_TEXT,
                    resolvedRequest,
                    0,
                    0,
                    operation + " skipped because chunking produced no chunks. " + REPLACE_SEMANTICS_MESSAGE
            );
        }

        List<EmbeddingResult> embeddings;
        try {
            embeddings = embeddingProvider.embedBatch(toEmbeddingRequests(candidates, resolvedRequest));
        } catch (RuntimeException ex) {
            return failed(resolvedRequest, candidates.size(), operation + " embedding failed: " + safeMessage(ex));
        }

        String validationFailure = validateEmbeddings(candidates, embeddings);
        if (!validationFailure.isBlank()) {
            return failed(resolvedRequest, candidates.size(), operation + " embedding validation failed: " + validationFailure);
        }

        int dimension = embeddings.get(0).dimension();
        if (vectorStoreProperties.isQdrantProvider()
                && dimension != vectorStoreProperties.getQdrant().getDimension()) {
            return failed(resolvedRequest, candidates.size(), operation
                    + " vector dimension mismatch: embedding dimension " + dimension
                    + " does not match Qdrant dimension " + vectorStoreProperties.getQdrant().getDimension() + ".");
        }

        List<DocumentChunkEntity> savedChunks = List.of();
        List<VectorPoint> points = List.of();
        boolean cleanupNeeded = false;
        try {
            vectorStoreClient.ensureReady();
            vectorStoreClient.deleteByDocumentId(
                    resolvedRequest.userId(),
                    resolvedRequest.documentId(),
                    resolvedRequest.indexVersion()
            );
            savedChunks = documentChunkService.replaceChunks(
                    resolvedRequest.documentId(),
                    resolvedRequest.userId(),
                    candidates,
                    resolvedRequest.indexVersion()
            );
            points = toVectorPoints(savedChunks, embeddings, resolvedEmbeddingModel(resolvedRequest, embeddings));
            cleanupNeeded = true;
            vectorStoreClient.upsert(points);
            documentChunkService.markIndexed(savedChunks);
            return result(
                    RagIndexingStatus.SUCCESS,
                    resolvedRequest,
                    savedChunks.size(),
                    points.size(),
                    operation + " completed. " + REPLACE_SEMANTICS_MESSAGE
            );
        } catch (RuntimeException ex) {
            if (!savedChunks.isEmpty()) {
                documentChunkService.markFailed(savedChunks);
            }
            if (cleanupNeeded) {
                cleanupVectors(resolvedRequest);
            }
            return result(
                    RagIndexingStatus.FAILED,
                    resolvedRequest,
                    savedChunks.isEmpty() ? candidates.size() : savedChunks.size(),
                    points.size(),
                    operation + " failed after embedding: " + safeMessage(ex) + " " + REPLACE_SEMANTICS_MESSAGE
            );
        }
    }

    private RagIndexingRequest validateAndResolve(RagIndexingRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        if (request.documentId() == null) {
            throw new IllegalArgumentException("documentId must not be null");
        }
        if (request.userId() == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        int indexVersion = request.indexVersion() == null
                ? DocumentChunkServiceImpl.DEFAULT_INDEX_VERSION
                : request.indexVersion();
        if (indexVersion <= 0) {
            throw new IllegalArgumentException("indexVersion must be positive");
        }
        return new RagIndexingRequest(
                request.documentId(),
                request.userId(),
                request.text(),
                indexVersion,
                request.embeddingModel()
        );
    }

    private List<EmbeddingRequest> toEmbeddingRequests(List<DocumentChunkCandidate> candidates,
                                                       RagIndexingRequest request) {
        String model = requestedEmbeddingModel(request);
        List<EmbeddingRequest> requests = new ArrayList<>(candidates.size());
        for (DocumentChunkCandidate candidate : candidates) {
            requests.add(new EmbeddingRequest(candidate.content(), model, embeddingMetadata(candidate, request)));
        }
        return requests;
    }

    private Map<String, String> embeddingMetadata(DocumentChunkCandidate candidate, RagIndexingRequest request) {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("userId", String.valueOf(request.userId()));
        metadata.put("documentId", String.valueOf(request.documentId()));
        metadata.put("indexVersion", String.valueOf(request.indexVersion()));
        metadata.put("chunkIndex", String.valueOf(candidate.chunkIndex()));
        metadata.put("contentHash", candidate.contentHash());
        metadata.putAll(candidate.structureMetadata());
        return metadata;
    }

    private String validateEmbeddings(List<DocumentChunkCandidate> candidates, List<EmbeddingResult> embeddings) {
        if (embeddings == null) {
            return "embedding result must not be null.";
        }
        if (embeddings.size() != candidates.size()) {
            return "embedding count " + embeddings.size() + " does not match chunk count " + candidates.size() + ".";
        }
        int dimension = -1;
        for (int i = 0; i < embeddings.size(); i++) {
            EmbeddingResult result = embeddings.get(i);
            if (result == null || result.vector() == null) {
                return "embedding vector at index " + i + " must not be null.";
            }
            if (result.vector().values().isEmpty()) {
                return "embedding vector at index " + i + " must not be empty.";
            }
            if (dimension < 0) {
                dimension = result.dimension();
            } else if (dimension != result.dimension()) {
                return "embedding dimension " + result.dimension()
                        + " at index " + i + " does not match " + dimension + ".";
            }
        }
        return "";
    }

    private List<VectorPoint> toVectorPoints(List<DocumentChunkEntity> chunks,
                                             List<EmbeddingResult> embeddings,
                                             String embeddingModel) {
        if (chunks.size() != embeddings.size()) {
            throw new IllegalStateException("saved chunk count must match embedding count.");
        }
        List<VectorPoint> points = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            DocumentChunkEntity chunk = chunks.get(i);
            EmbeddingVector vector = embeddings.get(i).vector();
            VectorPoint point = VectorPoint.fromDocumentChunk(chunk, vector, embeddingModel,
                    candidateStructureMetadata(i, embeddings));
            chunk.setVectorId(point.id());
            chunk.setEmbeddingModel(embeddingModel);
            points.add(point);
        }
        return points;
    }

    private Map<String, String> candidateStructureMetadata(int index,
                                                           List<EmbeddingResult> embeddings) {
        if (index < 0 || index >= embeddings.size()) {
            return Map.of();
        }
        Map<String, String> metadata = embeddings.get(index).metadata();
        if (metadata == null || metadata.isEmpty()) {
            return Map.of();
        }
        Map<String, String> structure = new LinkedHashMap<>();
        copyMetadata(metadata, structure, "sectionTitle");
        copyMetadata(metadata, structure, "sectionOrdinal");
        copyMetadata(metadata, structure, "sectionPath");
        copyMetadata(metadata, structure, "sourceBlockOrdinal");
        copyMetadata(metadata, structure, "structureType");
        copyMetadata(metadata, structure, "qualityFlags");
        return structure;
    }

    private void copyMetadata(Map<String, String> source, Map<String, String> target, String key) {
        String value = source.get(key);
        if (value != null && !value.isBlank()) {
            target.put(key, value);
        }
    }

    private String requestedEmbeddingModel(RagIndexingRequest request) {
        if (request.embeddingModel() != null && !request.embeddingModel().isBlank()) {
            return request.embeddingModel().trim();
        }
        return embeddingProperties.getModel();
    }

    private String resolvedEmbeddingModel(RagIndexingRequest request, List<EmbeddingResult> embeddings) {
        String requestedModel = requestedEmbeddingModel(request);
        if (!requestedModel.isBlank()) {
            return requestedModel;
        }
        for (EmbeddingResult embedding : embeddings) {
            if (embedding != null && embedding.model() != null && !embedding.model().isBlank()) {
                return embedding.model().trim();
            }
        }
        return "";
    }

    private RagIndexingResult failed(RagIndexingRequest request, int chunkCount, String message) {
        return result(RagIndexingStatus.FAILED, request, chunkCount, 0, message + " " + REPLACE_SEMANTICS_MESSAGE);
    }

    private RagIndexingResult result(RagIndexingStatus status,
                                     RagIndexingRequest request,
                                     int chunkCount,
                                     int vectorCount,
                                     String message) {
        return new RagIndexingResult(
                status,
                request.documentId(),
                request.userId(),
                request.indexVersion(),
                chunkCount,
                vectorCount,
                message
        );
    }

    private void cleanupVectors(RagIndexingRequest request) {
        try {
            vectorStoreClient.deleteByDocumentId(request.userId(), request.documentId(), request.indexVersion());
        } catch (RuntimeException ignored) {
            // Best-effort cleanup; retry/rebuild is the compensating path.
        }
    }

    private String safeMessage(RuntimeException ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return ex.getClass().getSimpleName();
        }
        return message;
    }
}
