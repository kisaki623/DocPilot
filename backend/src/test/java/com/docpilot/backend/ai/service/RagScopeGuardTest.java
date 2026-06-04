package com.docpilot.backend.ai.service;

import com.docpilot.backend.ai.rag.vector.VectorSearchHit;
import com.docpilot.backend.common.error.ErrorCode;
import com.docpilot.backend.common.exception.BusinessException;
import com.docpilot.backend.document.entity.Document;
import com.docpilot.backend.document.mapper.DocumentMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RagScopeGuardTest {

    private final DocumentMapper documentMapper = mock(DocumentMapper.class);
    private final RagScopeGuard guard = new RagScopeGuard(documentMapper);

    @Test
    void shouldAllowOwnedDocument() {
        when(documentMapper.selectById(61L)).thenReturn(document(61L, 7L));

        Document document = guard.requireOwnedDocument(7L, 61L);

        assertThat(document.getId()).isEqualTo(61L);
        assertThat(document.getUserId()).isEqualTo(7L);
    }

    @Test
    void shouldRejectMissingDocument() {
        when(documentMapper.selectById(61L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> guard.requireOwnedDocument(7L, 61L));

        assertEquals(ErrorCode.DOCUMENT_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void shouldRejectDocumentOwnedByAnotherUser() {
        when(documentMapper.selectById(61L)).thenReturn(document(61L, 8L));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> guard.requireOwnedDocument(7L, 61L));

        assertEquals(ErrorCode.DOCUMENT_FORBIDDEN, ex.getErrorCode());
    }

    @Test
    void shouldRejectHitOutsideRequestedScope() {
        BusinessException wrongUser = assertThrows(BusinessException.class,
                () -> guard.requireHitInScope(7L, 61L, 1, hit(8L, 61L, 1)));
        BusinessException wrongDocument = assertThrows(BusinessException.class,
                () -> guard.requireHitInScope(7L, 61L, 1, hit(7L, 62L, 1)));
        BusinessException wrongVersion = assertThrows(BusinessException.class,
                () -> guard.requireHitInScope(7L, 61L, 1, hit(7L, 61L, 2)));

        assertEquals(ErrorCode.DOCUMENT_FORBIDDEN, wrongUser.getErrorCode());
        assertEquals(ErrorCode.DOCUMENT_FORBIDDEN, wrongDocument.getErrorCode());
        assertEquals(ErrorCode.DOCUMENT_FORBIDDEN, wrongVersion.getErrorCode());
    }

    @Test
    void shouldAllowHitInRequestedScope() {
        guard.requireHitInScope(7L, 61L, 1, hit(7L, 61L, 1));
    }

    private Document document(Long documentId, Long userId) {
        Document document = new Document();
        document.setId(documentId);
        document.setUserId(userId);
        return document;
    }

    private VectorSearchHit hit(Long userId, Long documentId, Integer indexVersion) {
        return new VectorSearchHit(
                "vector-1",
                0.9D,
                userId,
                documentId,
                indexVersion,
                0,
                "scoped evidence",
                "hash-a",
                Map.of()
        );
    }
}
