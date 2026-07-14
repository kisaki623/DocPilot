package com.docpilot.backend.knowledge.controller;

import com.docpilot.backend.common.context.UserHolder;
import com.docpilot.backend.knowledge.dto.KnowledgeBaseAddDocumentsRequest;
import com.docpilot.backend.knowledge.dto.KnowledgeBaseCreateRequest;
import com.docpilot.backend.knowledge.service.KnowledgeBaseService;
import com.docpilot.backend.knowledge.vo.KnowledgeBaseDetailResponse;
import com.docpilot.backend.knowledge.vo.KnowledgeBaseDocumentMutationResponse;
import com.docpilot.backend.knowledge.vo.KnowledgeBaseResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeBaseControllerTest {

    private final KnowledgeBaseService knowledgeBaseService = mock(KnowledgeBaseService.class);
    private final KnowledgeBaseController controller = new KnowledgeBaseController(knowledgeBaseService);

    @AfterEach
    void clearUser() {
        UserHolder.clear();
    }

    @Test
    void shouldCreateWithCurrentUser() {
        UserHolder.setUserId(7L);
        KnowledgeBaseCreateRequest request = new KnowledgeBaseCreateRequest();
        request.setName("KB");
        when(knowledgeBaseService.create(org.mockito.Mockito.any(), org.mockito.Mockito.any(), org.mockito.Mockito.any()))
                .thenReturn(new KnowledgeBaseResponse());

        controller.create(request);

        verify(knowledgeBaseService).create(7L, "KB", null);
    }

    @Test
    void shouldListWithCurrentUser() {
        UserHolder.setUserId(7L);
        when(knowledgeBaseService.listByUser(7L)).thenReturn(List.of());

        controller.list();

        verify(knowledgeBaseService).listByUser(7L);
    }

    @Test
    void shouldGetDetailWithPathKnowledgeBaseId() {
        UserHolder.setUserId(7L);
        when(knowledgeBaseService.getDetail(7L, 10L)).thenReturn(new KnowledgeBaseDetailResponse());

        controller.detail(10L);

        verify(knowledgeBaseService).getDetail(7L, 10L);
    }

    @Test
    void shouldAddDocumentsWithCurrentUser() {
        UserHolder.setUserId(7L);
        KnowledgeBaseAddDocumentsRequest request = new KnowledgeBaseAddDocumentsRequest();
        request.setDocumentIds(List.of(101L, 102L));
        when(knowledgeBaseService.addDocuments(org.mockito.Mockito.any(), org.mockito.Mockito.any(), org.mockito.Mockito.any()))
                .thenReturn(new KnowledgeBaseDocumentMutationResponse());

        controller.addDocuments(10L, request);

        ArgumentCaptor<List<Long>> captor = ArgumentCaptor.forClass(List.class);
        verify(knowledgeBaseService).addDocuments(org.mockito.Mockito.eq(7L), org.mockito.Mockito.eq(10L), captor.capture());
        assertThat(captor.getValue()).containsExactly(101L, 102L);
    }

    @Test
    void shouldRemoveDocumentWithCurrentUser() {
        UserHolder.setUserId(7L);
        when(knowledgeBaseService.removeDocument(7L, 10L, 101L))
                .thenReturn(new KnowledgeBaseDocumentMutationResponse());

        controller.removeDocument(10L, 101L);

        verify(knowledgeBaseService).removeDocument(7L, 10L, 101L);
    }
}
