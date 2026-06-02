package com.docpilot.backend.ai.service.impl;

import com.docpilot.backend.ai.entity.DocumentChunkEntity;
import com.docpilot.backend.ai.mapper.DocumentChunkMapper;
import com.docpilot.backend.ai.rag.ChunkingService;
import com.docpilot.backend.ai.rag.DocumentChunkCandidate;
import com.docpilot.backend.ai.rag.DocumentChunkIndexStatus;
import com.docpilot.backend.ai.service.DocumentChunkService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class DocumentChunkServiceImpl implements DocumentChunkService {

    public static final int DEFAULT_INDEX_VERSION = 1;

    private final DocumentChunkMapper documentChunkMapper;
    private final ChunkingService chunkingService;

    public DocumentChunkServiceImpl(DocumentChunkMapper documentChunkMapper, ChunkingService chunkingService) {
        this.documentChunkMapper = documentChunkMapper;
        this.chunkingService = chunkingService;
    }

    @Override
    @Transactional
    public List<DocumentChunkEntity> saveChunks(Long documentId,
                                                Long userId,
                                                List<DocumentChunkCandidate> chunks,
                                                Integer indexVersion) {
        requireNonNull(documentId, "documentId");
        requireNonNull(userId, "userId");
        int resolvedVersion = resolveIndexVersion(indexVersion);
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }

        LocalDateTime now = LocalDateTime.now();
        List<DocumentChunkEntity> saved = new ArrayList<>(chunks.size());
        for (DocumentChunkCandidate chunk : chunks) {
            DocumentChunkEntity entity = toEntity(documentId, userId, chunk, resolvedVersion, now);
            documentChunkMapper.insert(entity);
            saved.add(entity);
        }
        return List.copyOf(saved);
    }

    @Override
    public List<DocumentChunkEntity> listByDocumentId(Long documentId) {
        requireNonNull(documentId, "documentId");
        List<DocumentChunkEntity> chunks = documentChunkMapper.selectByDocumentId(documentId);
        return chunks == null ? List.of() : chunks;
    }

    @Override
    @Transactional
    public int deleteByDocumentIdAndVersion(Long documentId, Integer indexVersion) {
        requireNonNull(documentId, "documentId");
        return documentChunkMapper.deleteByDocumentIdAndVersion(documentId, resolveIndexVersion(indexVersion));
    }

    @Override
    @Transactional
    public List<DocumentChunkEntity> replaceChunks(Long documentId, Long userId, String text, Integer indexVersion) {
        requireNonNull(documentId, "documentId");
        requireNonNull(userId, "userId");
        int resolvedVersion = resolveIndexVersion(indexVersion);
        documentChunkMapper.deleteByDocumentIdAndVersion(documentId, resolvedVersion);
        List<DocumentChunkCandidate> chunks = chunkingService.chunk(documentId, userId, text);
        return saveChunks(documentId, userId, chunks, resolvedVersion);
    }

    private DocumentChunkEntity toEntity(Long documentId,
                                         Long userId,
                                         DocumentChunkCandidate chunk,
                                         int indexVersion,
                                         LocalDateTime now) {
        DocumentChunkEntity entity = new DocumentChunkEntity();
        entity.setDocumentId(documentId);
        entity.setUserId(userId);
        entity.setChunkIndex(chunk.chunkIndex());
        entity.setContent(chunk.content());
        entity.setContentHash(chunk.contentHash());
        entity.setStartOffset(chunk.startOffset());
        entity.setEndOffset(chunk.endOffset());
        entity.setTokenCount(chunk.tokenCount());
        entity.setIndexStatus(DocumentChunkIndexStatus.PENDING);
        entity.setIndexVersion(indexVersion);
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        return entity;
    }

    private int resolveIndexVersion(Integer indexVersion) {
        int resolvedVersion = indexVersion == null ? DEFAULT_INDEX_VERSION : indexVersion;
        if (resolvedVersion <= 0) {
            throw new IllegalArgumentException("indexVersion must be positive");
        }
        return resolvedVersion;
    }

    private void requireNonNull(Object value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
    }
}
