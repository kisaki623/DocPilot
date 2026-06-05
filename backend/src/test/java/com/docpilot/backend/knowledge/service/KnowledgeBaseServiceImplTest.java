package com.docpilot.backend.knowledge.service;

import com.docpilot.backend.common.error.ErrorCode;
import com.docpilot.backend.common.exception.BusinessException;
import com.docpilot.backend.document.entity.Document;
import com.docpilot.backend.knowledge.constant.KnowledgeBaseStatus;
import com.docpilot.backend.knowledge.entity.KnowledgeBase;
import com.docpilot.backend.knowledge.entity.KnowledgeBaseDocument;
import com.docpilot.backend.knowledge.mapper.KnowledgeBaseDocumentMapper;
import com.docpilot.backend.knowledge.mapper.KnowledgeBaseMapper;
import com.docpilot.backend.knowledge.service.impl.KnowledgeBaseServiceImpl;
import com.docpilot.backend.knowledge.vo.KnowledgeBaseDocumentMutationResponse;
import com.docpilot.backend.knowledge.vo.KnowledgeBaseDocumentResponse;
import com.docpilot.backend.knowledge.vo.KnowledgeBaseResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeBaseServiceImplTest {

    private final KnowledgeBaseMapper knowledgeBaseMapper = mock(KnowledgeBaseMapper.class);
    private final KnowledgeBaseDocumentMapper knowledgeBaseDocumentMapper = mock(KnowledgeBaseDocumentMapper.class);
    private final KnowledgeBaseScopeGuard scopeGuard = mock(KnowledgeBaseScopeGuard.class);
    private final KnowledgeBaseServiceImpl service = new KnowledgeBaseServiceImpl(
            knowledgeBaseMapper,
            knowledgeBaseDocumentMapper,
            scopeGuard
    );

    @Test
    void shouldCreateKnowledgeBase() {
        KnowledgeBaseResponse response = service.create(7L, " Team KB ", " docs ");

        ArgumentCaptor<KnowledgeBase> captor = ArgumentCaptor.forClass(KnowledgeBase.class);
        verify(knowledgeBaseMapper).insert(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(7L);
        assertThat(captor.getValue().getName()).isEqualTo("Team KB");
        assertThat(captor.getValue().getDescription()).isEqualTo("docs");
        assertThat(captor.getValue().getStatus()).isEqualTo(KnowledgeBaseStatus.ACTIVE);
        assertThat(response.getName()).isEqualTo("Team KB");
    }

    @Test
    void shouldRejectBlankName() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.create(7L, " ", ""));

        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
        verify(knowledgeBaseMapper, never()).insert(org.mockito.Mockito.any(KnowledgeBase.class));
    }

    @Test
    void shouldListByUser() {
        when(knowledgeBaseMapper.selectActiveByUserId(7L)).thenReturn(List.of(kb(10L, 7L, "KB")));

        List<KnowledgeBaseResponse> result = service.listByUser(7L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(10L);
    }

    @Test
    void shouldReturnDetailWithActiveDocuments() {
        when(scopeGuard.requireOwnedKnowledgeBase(7L, 10L)).thenReturn(kb(10L, 7L, "KB"));
        KnowledgeBaseDocumentResponse doc = new KnowledgeBaseDocumentResponse();
        doc.setDocumentId(101L);
        doc.setDocumentTitle("Doc");
        when(knowledgeBaseDocumentMapper.selectActiveDocumentResponses(7L, 10L)).thenReturn(List.of(doc));

        assertThat(service.getDetail(7L, 10L).getDocuments())
                .singleElement()
                .extracting(KnowledgeBaseDocumentResponse::getDocumentTitle)
                .isEqualTo("Doc");
    }

    @Test
    void shouldAddDocumentsIdempotentlyAndRestoreRemovedRelation() {
        when(scopeGuard.requireOwnedKnowledgeBase(7L, 10L)).thenReturn(kb(10L, 7L, "KB"));
        when(scopeGuard.requireOwnedDocuments(7L, List.of(101L, 102L))).thenReturn(List.of(
                document(101L, 7L),
                document(102L, 7L)
        ));
        KnowledgeBaseDocument active = relation(10L, 101L, KnowledgeBaseStatus.ACTIVE);
        KnowledgeBaseDocument removed = relation(10L, 102L, KnowledgeBaseStatus.REMOVED);
        when(knowledgeBaseDocumentMapper.selectByKnowledgeBaseIdAndDocumentId(10L, 101L)).thenReturn(active);
        when(knowledgeBaseDocumentMapper.selectByKnowledgeBaseIdAndDocumentId(10L, 102L)).thenReturn(removed);
        when(knowledgeBaseDocumentMapper.countActiveDocuments(7L, 10L)).thenReturn(2);

        KnowledgeBaseDocumentMutationResponse response = service.addDocuments(7L, 10L, List.of(101L, 102L));

        verify(knowledgeBaseDocumentMapper, never()).insert(org.mockito.ArgumentMatchers.<KnowledgeBaseDocument>argThat(
                item -> item != null && Long.valueOf(101L).equals(item.getDocumentId())));
        verify(knowledgeBaseDocumentMapper).updateStatus(10L, 102L, KnowledgeBaseStatus.ACTIVE);
        assertThat(response.getActiveDocumentCount()).isEqualTo(2);
    }

    @Test
    void shouldSoftRemoveDocument() {
        when(scopeGuard.requireOwnedKnowledgeBase(7L, 10L)).thenReturn(kb(10L, 7L, "KB"));
        when(scopeGuard.requireOwnedDocument(7L, 101L)).thenReturn(document(101L, 7L));
        when(knowledgeBaseDocumentMapper.selectByKnowledgeBaseIdAndDocumentId(10L, 101L))
                .thenReturn(relation(10L, 101L, KnowledgeBaseStatus.ACTIVE));
        when(knowledgeBaseDocumentMapper.countActiveDocuments(7L, 10L)).thenReturn(0);

        service.removeDocument(7L, 10L, 101L);

        verify(knowledgeBaseDocumentMapper).updateStatus(10L, 101L, KnowledgeBaseStatus.REMOVED);
    }

    private KnowledgeBase kb(Long id, Long userId, String name) {
        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setId(id);
        knowledgeBase.setUserId(userId);
        knowledgeBase.setName(name);
        knowledgeBase.setStatus(KnowledgeBaseStatus.ACTIVE);
        return knowledgeBase;
    }

    private KnowledgeBaseDocument relation(Long knowledgeBaseId, Long documentId, String status) {
        KnowledgeBaseDocument relation = new KnowledgeBaseDocument();
        relation.setKnowledgeBaseId(knowledgeBaseId);
        relation.setDocumentId(documentId);
        relation.setStatus(status);
        return relation;
    }

    private Document document(Long id, Long userId) {
        Document document = new Document();
        document.setId(id);
        document.setUserId(userId);
        return document;
    }
}
