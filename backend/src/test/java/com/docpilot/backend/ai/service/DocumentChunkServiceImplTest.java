package com.docpilot.backend.ai.service;

import com.docpilot.backend.ai.entity.DocumentChunkEntity;
import com.docpilot.backend.ai.mapper.DocumentChunkMapper;
import com.docpilot.backend.ai.rag.ChunkingService;
import com.docpilot.backend.ai.rag.ChunkingServiceImpl;
import com.docpilot.backend.ai.rag.DocumentChunkCandidate;
import com.docpilot.backend.ai.rag.DocumentChunkIndexStatus;
import com.docpilot.backend.ai.service.impl.DocumentChunkServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentChunkServiceImplTest {

    @Mock
    private DocumentChunkMapper documentChunkMapper;

    private final ChunkingService chunkingService = new ChunkingServiceImpl();

    @Test
    void shouldSaveChunksWithPendingStatus() {
        DocumentChunkServiceImpl service = new DocumentChunkServiceImpl(documentChunkMapper, chunkingService);
        DocumentChunkCandidate candidate = new DocumentChunkCandidate(
                101L,
                7L,
                0,
                "Redis cache evidence.",
                "a".repeat(64),
                0,
                21,
                21
        );

        List<DocumentChunkEntity> saved = service.saveChunks(101L, 7L, List.of(candidate), null);

        assertThat(saved).hasSize(1);
        ArgumentCaptor<DocumentChunkEntity> captor = ArgumentCaptor.forClass(DocumentChunkEntity.class);
        verify(documentChunkMapper).insert(captor.capture());
        DocumentChunkEntity entity = captor.getValue();
        assertThat(entity.getDocumentId()).isEqualTo(101L);
        assertThat(entity.getUserId()).isEqualTo(7L);
        assertThat(entity.getChunkIndex()).isZero();
        assertThat(entity.getContent()).isEqualTo("Redis cache evidence.");
        assertThat(entity.getContentHash()).isEqualTo("a".repeat(64));
        assertThat(entity.getIndexStatus()).isEqualTo(DocumentChunkIndexStatus.PENDING);
        assertThat(entity.getIndexVersion()).isEqualTo(DocumentChunkServiceImpl.DEFAULT_INDEX_VERSION);
        assertThat(entity.getEmbeddingModel()).isNull();
        assertThat(entity.getVectorId()).isNull();
        assertThat(entity.getCreateTime()).isNotNull();
        assertThat(entity.getUpdateTime()).isNotNull();
    }

    @Test
    void shouldListByDocumentId() {
        DocumentChunkServiceImpl service = new DocumentChunkServiceImpl(documentChunkMapper, chunkingService);
        DocumentChunkEntity entity = new DocumentChunkEntity();
        entity.setDocumentId(101L);
        when(documentChunkMapper.selectByDocumentId(101L)).thenReturn(List.of(entity));

        List<DocumentChunkEntity> chunks = service.listByDocumentId(101L);

        assertThat(chunks).containsExactly(entity);
        verify(documentChunkMapper).selectByDocumentId(101L);
    }

    @Test
    void shouldDeleteByDocumentIdAndVersion() {
        DocumentChunkServiceImpl service = new DocumentChunkServiceImpl(documentChunkMapper, chunkingService);
        when(documentChunkMapper.deleteByDocumentIdAndVersion(101L, 2)).thenReturn(3);

        int deleted = service.deleteByDocumentIdAndVersion(101L, 2);

        assertThat(deleted).isEqualTo(3);
        verify(documentChunkMapper).deleteByDocumentIdAndVersion(101L, 2);
    }

    @Test
    void shouldReplaceChunksByDeletingVersionBeforeInsert() {
        DocumentChunkServiceImpl service = new DocumentChunkServiceImpl(documentChunkMapper, chunkingService);

        List<DocumentChunkEntity> saved = service.replaceChunks(101L, 7L, "first paragraph\n\nsecond paragraph", 2);

        assertThat(saved).hasSize(2);
        InOrder inOrder = inOrder(documentChunkMapper);
        inOrder.verify(documentChunkMapper).deleteByDocumentIdAndVersion(101L, 2);
        inOrder.verify(documentChunkMapper, times(2)).insert(any(DocumentChunkEntity.class));
        ArgumentCaptor<DocumentChunkEntity> captor = ArgumentCaptor.forClass(DocumentChunkEntity.class);
        verify(documentChunkMapper, times(2)).insert(captor.capture());
        assertThat(captor.getAllValues()).extracting(DocumentChunkEntity::getIndexVersion)
                .containsExactly(2, 2);
        assertThat(captor.getAllValues()).extracting(DocumentChunkEntity::getChunkIndex)
                .containsExactly(0, 1);
    }

    @Test
    void shouldReplaceBlankTextWithoutInsertingChunks() {
        DocumentChunkServiceImpl service = new DocumentChunkServiceImpl(documentChunkMapper, chunkingService);

        List<DocumentChunkEntity> saved = service.replaceChunks(101L, 7L, "   ", 1);

        assertThat(saved).isEmpty();
        verify(documentChunkMapper).deleteByDocumentIdAndVersion(101L, 1);
        verify(documentChunkMapper, org.mockito.Mockito.never()).insert(any(DocumentChunkEntity.class));
    }
}
