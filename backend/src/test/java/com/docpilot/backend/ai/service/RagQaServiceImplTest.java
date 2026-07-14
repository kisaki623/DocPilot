package com.docpilot.backend.ai.service;

import com.docpilot.backend.ai.entity.DocumentQaHistory;
import com.docpilot.backend.ai.exception.AiNonRetryableException;
import com.docpilot.backend.ai.exception.AiRetryableException;
import com.docpilot.backend.ai.mapper.DocumentQaHistoryMapper;
import com.docpilot.backend.ai.rag.RagEvidenceCitation;
import com.docpilot.backend.ai.rag.RagQaAnswer;
import com.docpilot.backend.ai.rag.RagQaProperties;
import com.docpilot.backend.ai.rag.RagQaQuery;
import com.docpilot.backend.ai.rag.RagRetrievalHit;
import com.docpilot.backend.ai.rag.RagRetrievalQuery;
import com.docpilot.backend.ai.rag.RagRetrievalResult;
import com.docpilot.backend.ai.service.impl.RagQaServiceImpl;
import com.docpilot.backend.ai.service.impl.AiRetryExecutor;
import com.docpilot.backend.common.error.ErrorCode;
import com.docpilot.backend.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagQaServiceImplTest {

    @Mock
    private RagDocumentRetrievalService retrievalService;

    @Mock
    private AiAnswerService aiAnswerService;

    @Mock
    private DocumentQaHistoryMapper documentQaHistoryMapper;

    @Test
    void shouldCallAiWithEvidencePromptAndSaveBasicHistory() {
        when(retrievalService.retrieve(any())).thenReturn(resultWithEvidence());
        when(aiAnswerService.answer(any(), any())).thenReturn("Use Redis cache [1].");
        RagQaServiceImpl service = service(new RagQaProperties());

        RagQaAnswer answer = service.answer(new RagQaQuery(7L, 101L, "How cache works?", 3, 1, "s1"));

        assertThat(answer.answer()).isEqualTo("Use Redis cache [1].");
        assertThat(answer.retrieval().citations()).hasSize(1);
        ArgumentCaptor<String> contextCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(aiAnswerService).answer(contextCaptor.capture(), promptCaptor.capture());
        assertThat(contextCaptor.getValue()).contains("[1]").contains("Redis cache evidence");
        assertThat(promptCaptor.getValue()).contains("using only the numbered evidence");
        ArgumentCaptor<DocumentQaHistory> historyCaptor = ArgumentCaptor.forClass(DocumentQaHistory.class);
        verify(documentQaHistoryMapper).insert(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getQuestion()).isEqualTo("How cache works?");
        assertThat(historyCaptor.getValue().getAnswer()).isEqualTo("Use Redis cache [1].");
    }

    @Test
    void shouldReturnNoEvidenceFallbackWithoutCallingAi() {
        when(retrievalService.retrieve(any())).thenReturn(resultWithoutEvidence());
        RagQaServiceImpl service = service(new RagQaProperties());

        RagQaAnswer answer = service.answer(new RagQaQuery(7L, 101L, "missing?", null, null, ""));

        assertThat(answer.noEvidence()).isTrue();
        assertThat(answer.fallbackReason()).isEqualTo("no_evidence");
        assertThat(answer.answer()).contains("未在当前文档索引中检索到足够证据");
        verify(aiAnswerService, never()).answer(any(), any());
        verify(documentQaHistoryMapper).insert(any(DocumentQaHistory.class));
    }

    @Test
    void shouldReturnRetrievalUnavailableFallbackWithoutCallingAi() {
        when(retrievalService.retrieve(any())).thenThrow(new IllegalStateException("connection refused at 10.0.0.1"));
        RagQaServiceImpl service = service(new RagQaProperties());

        RagQaAnswer answer = service.answer(new RagQaQuery(7L, 101L, "question", 3, 1, ""));

        assertThat(answer.fallbackReason()).isEqualTo("retrieval_unavailable");
        assertThat(answer.answer()).contains("RAG 检索暂不可用");
        verify(aiAnswerService, never()).answer(any(), any());
    }

    @Test
    void shouldNotFallbackOrSaveHistoryWhenRetrievalRejectsScope() {
        when(retrievalService.retrieve(any())).thenThrow(new BusinessException(ErrorCode.DOCUMENT_FORBIDDEN));
        RagQaServiceImpl service = service(new RagQaProperties());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.answer(new RagQaQuery(7L, 101L, "question", 3, 1, "")));

        assertEquals(ErrorCode.DOCUMENT_FORBIDDEN, ex.getErrorCode());
        verify(aiAnswerService, never()).answer(any(), any());
        verify(documentQaHistoryMapper, never()).insert(any(DocumentQaHistory.class));
    }

    @Test
    void shouldNotMaskGenerationFailureAsRetrievalFallback() {
        when(retrievalService.retrieve(any())).thenReturn(resultWithEvidence());
        when(aiAnswerService.answer(any(), any())).thenThrow(new IllegalStateException("model down"));
        RagQaServiceImpl service = service(new RagQaProperties());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.answer(new RagQaQuery(7L, 101L, "question", 3, 1, "")));

        assertEquals(ErrorCode.AI_CALL_FAILED, ex.getErrorCode());
    }

    @Test
    void shouldReportHistoryPersistenceFailureAsAiCallFailureWithoutRetryingRetrieval() {
        when(retrievalService.retrieve(any())).thenReturn(resultWithEvidence());
        when(aiAnswerService.answer(any(), any())).thenReturn("Use Redis cache [1].");
        when(documentQaHistoryMapper.insert(any(DocumentQaHistory.class)))
                .thenThrow(new IllegalStateException("history store unavailable"));
        RagQaServiceImpl service = service(new RagQaProperties());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.answer(new RagQaQuery(7L, 101L, "question", 3, 1, "")));

        assertEquals(ErrorCode.AI_CALL_FAILED, ex.getErrorCode());
        verify(aiAnswerService, times(1)).answer(any(), any());
        verify(documentQaHistoryMapper, times(1)).insert(any(DocumentQaHistory.class));
    }

    @Test
    void shouldRetryOnlyRetryableModelCallAndSaveHistoryOnceAfterSuccess() {
        when(retrievalService.retrieve(any())).thenReturn(resultWithEvidence());
        when(aiAnswerService.answer(any(), any()))
                .thenThrow(new AiRetryableException("temporary model failure"))
                .thenReturn("Use Redis cache [1].");
        RagQaServiceImpl service = serviceWithRetry(new RagQaProperties());

        RagQaAnswer answer = service.answer(new RagQaQuery(7L, 101L, "question", 3, 1, ""));

        assertThat(answer.answer()).isEqualTo("Use Redis cache [1].");
        verify(retrievalService, times(1)).retrieve(any());
        verify(aiAnswerService, times(2)).answer(any(), any());
        verify(documentQaHistoryMapper, times(1)).insert(any(DocumentQaHistory.class));
    }

    @Test
    void shouldNotRetryNonRetryableModelFailure() {
        when(retrievalService.retrieve(any())).thenReturn(resultWithEvidence());
        when(aiAnswerService.answer(any(), any())).thenThrow(new AiNonRetryableException("invalid request"));
        RagQaServiceImpl service = serviceWithRetry(new RagQaProperties());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.answer(new RagQaQuery(7L, 101L, "question", 3, 1, "")));

        assertEquals(ErrorCode.AI_CALL_FAILED, ex.getErrorCode());
        verify(retrievalService, times(1)).retrieve(any());
        verify(aiAnswerService, times(1)).answer(any(), any());
        verify(documentQaHistoryMapper, never()).insert(any(DocumentQaHistory.class));
    }

    @Test
    void shouldEmitRagSseEventsInOrder() {
        when(retrievalService.retrieve(any())).thenReturn(resultWithEvidence());
        doAnswer(invocation -> {
            Consumer<String> consumer = invocation.getArgument(2);
            consumer.accept("answer ");
            consumer.accept("chunk");
            return null;
        }).when(aiAnswerService).streamAnswer(any(), any(), any());
        RecordingRagQaService service = new RecordingRagQaService(
                retrievalService,
                aiAnswerService,
                documentQaHistoryMapper,
                new RagQaProperties()
        );

        service.streamAnswer(new RagQaQuery(7L, 101L, "How cache works?", 3, 1, "s1"));

        assertThat(service.events).containsExactly("meta", "retrieval", "citation", "chunk", "chunk", "done");
        verify(documentQaHistoryMapper).insert(any(DocumentQaHistory.class));
    }

    @Test
    void shouldEmitNoEvidenceSseWithoutAiStream() {
        when(retrievalService.retrieve(any())).thenReturn(resultWithoutEvidence());
        RecordingRagQaService service = new RecordingRagQaService(
                retrievalService,
                aiAnswerService,
                documentQaHistoryMapper,
                new RagQaProperties()
        );

        service.streamAnswer(new RagQaQuery(7L, 101L, "missing?", 3, 1, "s1"));

        assertThat(service.events).containsExactly("meta", "retrieval", "chunk", "done");
        verify(aiAnswerService, never()).streamAnswer(any(), any(), any());
    }

    @Test
    void shouldEmitRetrievalFallbackWithoutTerminalError() {
        when(retrievalService.retrieve(any())).thenThrow(new IllegalStateException("vector unavailable"));
        RecordingRagQaService service = new RecordingRagQaService(
                retrievalService, aiAnswerService, documentQaHistoryMapper, new RagQaProperties()
        );

        service.streamAnswer(new RagQaQuery(7L, 101L, "How cache works?", 3, 1, "s1"));

        assertThat(service.events).containsExactly("meta", "fallback", "chunk", "done");
        verify(aiAnswerService, never()).streamAnswer(any(), any(), any());
    }

    @Test
    void shouldEmitGenerationErrorBeforeAnyChunkWithoutRetrievalFallback() {
        when(retrievalService.retrieve(any())).thenReturn(resultWithEvidence());
        doAnswer(invocation -> {
            throw new IllegalStateException("model unavailable");
        }).when(aiAnswerService).streamAnswer(any(), any(), any());
        RecordingRagQaService service = new RecordingRagQaService(
                retrievalService, aiAnswerService, documentQaHistoryMapper, new RagQaProperties()
        );

        service.streamAnswer(new RagQaQuery(7L, 101L, "How cache works?", 3, 1, "s1"));

        assertThat(service.events).containsExactly("meta", "retrieval", "citation", "error");
        assertThat(service.errorStages).containsExactly("generation");
        verify(documentQaHistoryMapper, never()).insert(any(DocumentQaHistory.class));
    }

    @Test
    void shouldEmitPartialGenerationErrorAfterChunkWithoutSavingHistory() {
        when(retrievalService.retrieve(any())).thenReturn(resultWithEvidence());
        doAnswer(invocation -> {
            Consumer<String> consumer = invocation.getArgument(2);
            consumer.accept("partial answer");
            throw new IllegalStateException("model stream interrupted");
        }).when(aiAnswerService).streamAnswer(any(), any(), any());
        RecordingRagQaService service = new RecordingRagQaService(
                retrievalService, aiAnswerService, documentQaHistoryMapper, new RagQaProperties()
        );

        service.streamAnswer(new RagQaQuery(7L, 101L, "How cache works?", 3, 1, "s1"));

        assertThat(service.events).containsExactly("meta", "retrieval", "citation", "chunk", "error");
        assertThat(service.errorStages).containsExactly("generation_partial");
        verify(documentQaHistoryMapper, never()).insert(any(DocumentQaHistory.class));
    }

    @Test
    void shouldEmitScopeErrorSseWithoutFallbackOrAiStream() {
        when(retrievalService.retrieve(any())).thenThrow(new BusinessException(ErrorCode.DOCUMENT_FORBIDDEN));
        RecordingRagQaService service = new RecordingRagQaService(
                retrievalService,
                aiAnswerService,
                documentQaHistoryMapper,
                new RagQaProperties()
        );

        service.streamAnswer(new RagQaQuery(7L, 101L, "forbidden?", 3, 1, "s1"));

        assertThat(service.events).containsExactly("meta", "error");
        verify(aiAnswerService, never()).streamAnswer(any(), any(), any());
        verify(documentQaHistoryMapper, never()).insert(any(DocumentQaHistory.class));
    }

    private RagQaServiceImpl service(RagQaProperties properties) {
        return new RagQaServiceImpl(retrievalService, aiAnswerService, documentQaHistoryMapper, properties);
    }

    private RagQaServiceImpl serviceWithRetry(RagQaProperties properties) {
        AiRetryExecutor retryExecutor = new AiRetryExecutor();
        ReflectionTestUtils.setField(retryExecutor, "retryEnabled", true);
        ReflectionTestUtils.setField(retryExecutor, "maxAttempts", 3);
        ReflectionTestUtils.setField(retryExecutor, "initialBackoffMs", 1L);
        ReflectionTestUtils.setField(retryExecutor, "backoffMultiplier", 2.0D);
        ReflectionTestUtils.setField(retryExecutor, "maxBackoffMs", 10L);
        return new RagQaServiceImpl(retrievalService, aiAnswerService, documentQaHistoryMapper, properties, retryExecutor);
    }

    private RagRetrievalResult resultWithEvidence() {
        RagRetrievalHit hit = new RagRetrievalHit(
                1,
                "vector-1",
                0.9D,
                7L,
                101L,
                1,
                501L,
                0,
                "Redis cache evidence",
                "hash-a",
                0,
                20,
                3,
                "mock-model"
        );
        return new RagRetrievalResult(
                7L,
                101L,
                "How cache works?",
                3,
                1,
                List.of(hit),
                List.of(hit.toCitation()),
                false,
                "in_memory",
                "",
                "mock-model"
        );
    }

    private RagRetrievalResult resultWithoutEvidence() {
        return new RagRetrievalResult(
                7L,
                101L,
                "missing?",
                3,
                1,
                List.of(),
                List.of(),
                true,
                "in_memory",
                "",
                "mock-model"
        );
    }

    private static final class RecordingRagQaService extends RagQaServiceImpl {

        private final List<String> events = new ArrayList<>();
        private final List<String> errorStages = new ArrayList<>();

        private RecordingRagQaService(RagDocumentRetrievalService retrievalService,
                                      AiAnswerService aiAnswerService,
                                      DocumentQaHistoryMapper documentQaHistoryMapper,
                                      RagQaProperties ragQaProperties) {
            super(retrievalService, aiAnswerService, documentQaHistoryMapper, ragQaProperties);
        }

        @Override
        protected void startStreamWorker(Runnable task) {
            task.run();
        }

        @Override
        protected void send(SseEmitter emitter, String eventName, Object data) {
            events.add(eventName);
            if ("citation".equals(eventName)) {
                assertThat(data).isInstanceOf(RagEvidenceCitation.class);
            }
            if ("error".equals(eventName) && data instanceof Map<?, ?> payload) {
                errorStages.add(String.valueOf(payload.get("stage")));
            }
        }
    }
}
