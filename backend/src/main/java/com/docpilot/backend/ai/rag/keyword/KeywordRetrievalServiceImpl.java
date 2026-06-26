package com.docpilot.backend.ai.rag.keyword;

import com.docpilot.backend.ai.entity.DocumentChunkEntity;
import com.docpilot.backend.ai.mapper.DocumentChunkMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation of KeywordRetrievalService using BM25 scoring.
 * Retrieves chunks from MySQL and scores them in-memory using BM25.
 */
@Service
public class KeywordRetrievalServiceImpl implements KeywordRetrievalService {

    private final DocumentChunkMapper chunkMapper;

    public KeywordRetrievalServiceImpl(DocumentChunkMapper chunkMapper) {
        this.chunkMapper = chunkMapper;
    }

    @Override
    public List<KeywordSearchHit> search(String query, Long userId, List<Long> documentIds, Integer indexVersion, int topK) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        if (userId == null) {
            return List.of();
        }
        if (documentIds == null || documentIds.isEmpty()) {
            return List.of();
        }
        if (topK <= 0) {
            return List.of();
        }
        if (indexVersion == null || indexVersion <= 0) {
            return List.of();
        }

        // Retrieve all chunks for the given documents
        LambdaQueryWrapper<DocumentChunkEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DocumentChunkEntity::getUserId, userId)
                .in(DocumentChunkEntity::getDocumentId, documentIds)
                .eq(DocumentChunkEntity::getIndexVersion, indexVersion)
                .eq(DocumentChunkEntity::getIndexStatus, "INDEXED")
                .orderBy(true, true, DocumentChunkEntity::getDocumentId, DocumentChunkEntity::getChunkIndex);

        List<DocumentChunkEntity> chunks = chunkMapper.selectList(wrapper);
        if (chunks.isEmpty()) {
            return List.of();
        }

        // Initialize BM25 scorer with corpus
        List<String> corpus = chunks.stream()
                .map(DocumentChunkEntity::getContent)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        BM25Scorer bm25Scorer = new BM25Scorer();
        bm25Scorer.initializeCorpus(corpus);

        // Score each chunk
        List<KeywordSearchHit> hits = new ArrayList<>(chunks.size());
        for (DocumentChunkEntity chunk : chunks) {
            if (chunk.getContent() == null || chunk.getContent().isBlank()) {
                continue;
            }
            double score = bm25Scorer.score(query, chunk.getContent());
            if (score > 0) {
                hits.add(new KeywordSearchHit(
                        chunk.getId(),
                        chunk.getDocumentId(),
                        chunk.getUserId(),
                        chunk.getIndexVersion(),
                        chunk.getChunkIndex(),
                        chunk.getContent(),
                        chunk.getContentHash(),
                        chunk.getStartOffset(),
                        chunk.getEndOffset(),
                        chunk.getTokenCount(),
                        chunk.getEmbeddingModel(),
                        score
                ));
            }
        }

        // Sort by score descending and take topK
        hits.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        return hits.stream()
                .limit(topK)
                .collect(Collectors.toList());
    }
}
