package com.docpilot.backend.knowledge.service;

import com.docpilot.backend.ai.rag.vector.VectorSearchHit;
import com.docpilot.backend.common.error.ErrorCode;
import com.docpilot.backend.common.exception.BusinessException;
import com.docpilot.backend.document.entity.Document;
import com.docpilot.backend.document.mapper.DocumentMapper;
import com.docpilot.backend.knowledge.entity.KnowledgeBase;
import com.docpilot.backend.knowledge.mapper.KnowledgeBaseDocumentMapper;
import com.docpilot.backend.knowledge.mapper.KnowledgeBaseMapper;
import com.docpilot.backend.knowledge.vo.KnowledgeBaseDocumentResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeBaseScopeGuardTest {

    private final KnowledgeBaseMapper knowledgeBaseMapper = mock(KnowledgeBaseMapper.class);
    private final KnowledgeBaseDocumentMapper knowledgeBaseDocumentMapper = mock(KnowledgeBaseDocumentMapper.class);
    private final DocumentMapper documentMapper = mock(DocumentMapper.class);
    private final KnowledgeBaseScopeGuard guard = new KnowledgeBaseScopeGuard(
            knowledgeBaseMapper,
            knowledgeBaseDocumentMapper,
            documentMapper
    );

    @Test
    void shouldRequireOwnedKnowledgeBase() {
        when(knowledgeBaseMapper.selectById(10L)).thenReturn(kb(10L, 7L));

        KnowledgeBase result = guard.requireOwnedKnowledgeBase(7L, 10L);

        assertThat(result.getId()).isEqualTo(10L);
    }

    @Test
    void shouldRejectKnowledgeBaseOwnedByAnotherUser() {
        when(knowledgeBaseMapper.selectById(10L)).thenReturn(kb(10L, 8L));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> guard.requireOwnedKnowledgeBase(7L, 10L));

        assertEquals(ErrorCode.KNOWLEDGE_BASE_FORBIDDEN, ex.getErrorCode());
    }

    @Test
    void shouldRejectDocumentOwnedByAnotherUser() {
        when(documentMapper.selectById(101L)).thenReturn(document(101L, 8L));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> guard.requireOwnedDocument(7L, 101L));

        assertEquals(ErrorCode.DOCUMENT_FORBIDDEN, ex.getErrorCode());
    }

    @Test
    void shouldListActiveDocumentsAfterKnowledgeBaseScopeCheck() {
        when(knowledgeBaseMapper.selectById(10L)).thenReturn(kb(10L, 7L));
        KnowledgeBaseDocumentResponse doc = new KnowledgeBaseDocumentResponse();
        doc.setDocumentId(101L);
        when(knowledgeBaseDocumentMapper.selectActiveDocumentResponses(7L, 10L)).thenReturn(List.of(doc));

        List<KnowledgeBaseDocumentResponse> result = guard.listActiveKnowledgeBaseDocuments(7L, 10L);

        assertThat(result).hasSize(1);
        verify(knowledgeBaseDocumentMapper).selectActiveDocumentResponses(7L, 10L);
    }

    @Test
    void shouldRejectVectorHitOutsideKnowledgeBaseScope() {
        VectorSearchHit hit = new VectorSearchHit(
                "v1",
                0.9D,
                7L,
                102L,
                1,
                0,
                "content",
                "hash",
                Map.of()
        );

        BusinessException ex = assertThrows(BusinessException.class,
                () -> guard.requireHitInKnowledgeBaseScope(7L, 10L, Set.of(101L), 1, hit));

        assertEquals(ErrorCode.KNOWLEDGE_BASE_FORBIDDEN, ex.getErrorCode());
    }

    private KnowledgeBase kb(Long id, Long userId) {
        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setId(id);
        knowledgeBase.setUserId(userId);
        knowledgeBase.setStatus("ACTIVE");
        return knowledgeBase;
    }

    private Document document(Long id, Long userId) {
        Document document = new Document();
        document.setId(id);
        document.setUserId(userId);
        return document;
    }
}
