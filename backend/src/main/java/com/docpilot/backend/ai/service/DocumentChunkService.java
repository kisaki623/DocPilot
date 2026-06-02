package com.docpilot.backend.ai.service;

import com.docpilot.backend.ai.entity.DocumentChunkEntity;
import com.docpilot.backend.ai.rag.DocumentChunkCandidate;

import java.util.List;

public interface DocumentChunkService {

    List<DocumentChunkEntity> saveChunks(Long documentId,
                                         Long userId,
                                         List<DocumentChunkCandidate> chunks,
                                         Integer indexVersion);

    List<DocumentChunkEntity> listByDocumentId(Long documentId);

    int deleteByDocumentIdAndVersion(Long documentId, Integer indexVersion);

    List<DocumentChunkEntity> replaceChunks(Long documentId, Long userId, String text, Integer indexVersion);
}
