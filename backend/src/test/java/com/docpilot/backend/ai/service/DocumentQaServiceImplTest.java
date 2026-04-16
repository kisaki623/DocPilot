package com.docpilot.backend.ai.service;

import com.docpilot.backend.ai.mapper.DocumentQaHistoryMapper;
import com.docpilot.backend.ai.service.impl.AiRetryExecutor;
import com.docpilot.backend.ai.service.impl.DocumentQaServiceImpl;
import com.docpilot.backend.ai.entity.DocumentQaHistory;
import com.docpilot.backend.ai.exception.AiNonRetryableException;
import com.docpilot.backend.ai.exception.AiRetryableException;
import com.docpilot.backend.ai.vo.DocumentQaHistoryItemResponse;
import com.docpilot.backend.ai.vo.DocumentQaResponse;
import com.docpilot.backend.common.constant.CommonConstants;
import com.docpilot.backend.common.error.ErrorCode;
import com.docpilot.backend.common.exception.BusinessException;
import com.docpilot.backend.common.limiter.RedisTokenBucketRateLimiter;
import com.docpilot.backend.document.entity.Document;
import com.docpilot.backend.document.mapper.DocumentMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.function.Consumer;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

@ExtendWith(MockitoExtension.class)
class DocumentQaServiceImplTest {

    @Mock
    private DocumentMapper documentMapper;

    @Mock
    private AiAnswerService aiAnswerService;

    @Mock
    private DocumentQaHistoryMapper documentQaHistoryMapper;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private RedisTokenBucketRateLimiter redisTokenBucketRateLimiter;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ListOperations<String, String> listOperations;

    private DocumentQaServiceImpl buildService() {
        AiRetryExecutor aiRetryExecutor = new AiRetryExecutor();
        ReflectionTestUtils.setField(aiRetryExecutor, "retryEnabled", true);
        ReflectionTestUtils.setField(aiRetryExecutor, "maxAttempts", 3);
        ReflectionTestUtils.setField(aiRetryExecutor, "initialBackoffMs", 1L);
        ReflectionTestUtils.setField(aiRetryExecutor, "backoffMultiplier", 2.0D);
        ReflectionTestUtils.setField(aiRetryExecutor, "maxBackoffMs", 4L);

        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(stringRedisTemplate.opsForList()).thenReturn(listOperations);
        lenient().when(redisTokenBucketRateLimiter.tryConsume(
                anyString(),
                eq(CommonConstants.AI_QA_TOKEN_BUCKET_CAPACITY),
                eq(CommonConstants.AI_QA_TOKEN_BUCKET_REFILL_TOKENS),
                eq(CommonConstants.AI_QA_TOKEN_BUCKET_REFILL_INTERVAL_SECONDS)
        )).thenReturn(true);
        return new DocumentQaServiceImpl(
                documentMapper,
                aiAnswerService,
                documentQaHistoryMapper,
                stringRedisTemplate,
                redisTokenBucketRateLimiter,
                aiRetryExecutor
        );
    }

    @Test
    void shouldAnswerWhenDocumentIsOwnedByCurrentUser() {
        DocumentQaServiceImpl documentQaService = buildService();

        Document document = new Document();
        document.setId(101L);
        document.setUserId(100L);
        document.setContent("This is a parsed document content for QA tests.");
        when(documentMapper.selectById(101L)).thenReturn(document);
        when(aiAnswerService.answer("This is a parsed document content for QA tests.", "What is this document about?"))
                .thenReturn("It is about QA tests.");
        when(documentQaHistoryMapper.insert(any(DocumentQaHistory.class))).thenReturn(1);

        DocumentQaResponse response = documentQaService.answer(100L, 101L, "What is this document about?");

        assertEquals(101L, response.getDocumentId());
        assertEquals("What is this document about?", response.getQuestion());
        assertEquals("It is about QA tests.", response.getAnswer());
        assertNotNull(response.getCitations());
        assertFalse(response.getCitations().isEmpty());
        verify(documentQaHistoryMapper).insert(any(DocumentQaHistory.class));
    }

    @Test
    void shouldReturnCitationLocationAndSnippet() {
        DocumentQaServiceImpl documentQaService = buildService();

        Document document = new Document();
        document.setId(101L);
        document.setUserId(100L);
        document.setContent("第一段介绍系统背景。\n\n第二段强调缓存与限流策略。\n\n第三段说明引用定位实现。");
        when(documentMapper.selectById(101L)).thenReturn(document);
        when(aiAnswerService.answer(any(), eq("引用定位实现在哪"))).thenReturn("在第三段。");
        when(documentQaHistoryMapper.insert(any(DocumentQaHistory.class))).thenReturn(1);

        DocumentQaResponse response = documentQaService.answer(100L, 101L, "引用定位实现在哪");

        assertNotNull(response.getCitations());
        assertFalse(response.getCitations().isEmpty());
        DocumentQaResponse.CitationItem citation = response.getCitations().get(0);
        assertNotNull(citation.getChunkIndex());
        assertNotNull(citation.getCharStart());
        assertNotNull(citation.getCharEnd());
        assertTrue(citation.getCharEnd() > citation.getCharStart());
        assertNotNull(citation.getSnippet());
        assertFalse(citation.getSnippet().isBlank());
    }

    @Test
    void shouldThrowWhenQuestionIsBlank() {
        DocumentQaServiceImpl documentQaService = buildService();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> documentQaService.answer(100L, 101L, "   "));

        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
    }

    @Test
    void shouldThrowWhenDocumentNotFound() {
        DocumentQaServiceImpl documentQaService = buildService();
        when(documentMapper.selectById(101L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> documentQaService.answer(100L, 101L, "question"));

        assertEquals(ErrorCode.DOCUMENT_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void shouldThrowWhenDocumentBelongsToAnotherUser() {
        DocumentQaServiceImpl documentQaService = buildService();

        Document document = new Document();
        document.setId(101L);
        document.setUserId(200L);
        document.setContent("content");
        when(documentMapper.selectById(101L)).thenReturn(document);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> documentQaService.answer(100L, 101L, "question"));

        assertEquals(ErrorCode.DOCUMENT_FORBIDDEN, ex.getErrorCode());
    }

    @Test
    void shouldThrowWhenDocumentContentIsEmpty() {
        DocumentQaServiceImpl documentQaService = buildService();

        Document document = new Document();
        document.setId(101L);
        document.setUserId(100L);
        document.setContent("   ");
        when(documentMapper.selectById(101L)).thenReturn(document);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> documentQaService.answer(100L, 101L, "question"));

        assertEquals(ErrorCode.DOCUMENT_CONTENT_EMPTY, ex.getErrorCode());
    }

    @Test
    void shouldThrowWhenAiCallFails() {
        DocumentQaServiceImpl documentQaService = buildService();

        Document document = new Document();
        document.setId(101L);
        document.setUserId(100L);
        document.setContent("content");
        when(documentMapper.selectById(101L)).thenReturn(document);
        when(aiAnswerService.answer("content", "question")).thenThrow(new RuntimeException("boom"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> documentQaService.answer(100L, 101L, "question"));

        assertEquals(ErrorCode.AI_CALL_FAILED, ex.getErrorCode());
        verify(documentQaHistoryMapper, never()).insert(any(DocumentQaHistory.class));
    }

    @Test
    void shouldTrimLongDocumentContextBeforeAiCall() {
        DocumentQaServiceImpl documentQaService = buildService();

        String longContent = "A".repeat(4500);
        Document document = new Document();
        document.setId(101L);
        document.setUserId(100L);
        document.setContent(longContent);
        when(documentMapper.selectById(101L)).thenReturn(document);
        when(aiAnswerService.answer(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq("question")))
                .thenReturn("ok");
        when(documentQaHistoryMapper.insert(any(DocumentQaHistory.class))).thenReturn(1);

        DocumentQaResponse response = documentQaService.answer(100L, 101L, "question");

        assertEquals("ok", response.getAnswer());
        ArgumentCaptor<String> contextCaptor = ArgumentCaptor.forClass(String.class);
        verify(aiAnswerService).answer(contextCaptor.capture(), org.mockito.ArgumentMatchers.eq("question"));
        assertEquals(4000, contextCaptor.getValue().length());
    }

    @Test
    void shouldApplyConfiguredMaxContextLengthBeforeAiCall() {
        DocumentQaServiceImpl documentQaService = buildService();
        ReflectionTestUtils.setField(documentQaService, "maxDocumentContextLength", 50);

        String longContent = "A".repeat(200);
        Document document = new Document();
        document.setId(101L);
        document.setUserId(100L);
        document.setContent(longContent);
        when(documentMapper.selectById(101L)).thenReturn(document);
        when(aiAnswerService.answer(any(), eq("question"))).thenReturn("ok");
        when(documentQaHistoryMapper.insert(any(DocumentQaHistory.class))).thenReturn(1);

        documentQaService.answer(100L, 101L, "question");

        ArgumentCaptor<String> contextCaptor = ArgumentCaptor.forClass(String.class);
        verify(aiAnswerService).answer(contextCaptor.capture(), eq("question"));
        assertEquals(50, contextCaptor.getValue().length());
    }

    @Test
    void shouldUseSameNormalizedContextForAnswerAndStream() {
        DocumentQaServiceImpl documentQaService = buildService();

        String content = "Line1\n\n\tLine2    Line3";
        Document document = new Document();
        document.setId(101L);
        document.setUserId(100L);
        document.setContent(content);
        when(documentMapper.selectById(101L)).thenReturn(document);
        when(aiAnswerService.answer(any(), eq("same question"))).thenReturn("ok");
        when(documentQaHistoryMapper.insert(any(DocumentQaHistory.class))).thenReturn(1);
        doAnswer(invocation -> {
            Consumer<String> consumer = invocation.getArgument(2);
            consumer.accept("chunk");
            return null;
        }).when(aiAnswerService).streamAnswer(any(), eq("same question"), any());

        documentQaService.answer(100L, 101L, "same question");
        documentQaService.streamAnswer(100L, 101L, "same question");

        ArgumentCaptor<String> answerContextCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> streamContextCaptor = ArgumentCaptor.forClass(String.class);
        verify(aiAnswerService).answer(answerContextCaptor.capture(), eq("same question"));
        verify(aiAnswerService, timeout(2000)).streamAnswer(streamContextCaptor.capture(), eq("same question"), any());
        assertEquals("Line1 Line2 Line3", answerContextCaptor.getValue());
        assertEquals(answerContextCaptor.getValue(), streamContextCaptor.getValue());
    }

    @Test
    void shouldStreamAnswerWhenDocumentIsOwnedByCurrentUser() {
        DocumentQaServiceImpl documentQaService = buildService();

        Document document = new Document();
        document.setId(101L);
        document.setUserId(100L);
        document.setContent("This is a parsed document content for stream tests.");
        when(documentMapper.selectById(101L)).thenReturn(document);

        doAnswer(invocation -> {
            java.util.function.Consumer<String> consumer = invocation.getArgument(2);
            consumer.accept("chunk-1");
            consumer.accept("chunk-2");
            return null;
        }).when(aiAnswerService).streamAnswer(eq("This is a parsed document content for stream tests."), eq("stream question"), any());

        SseEmitter emitter = documentQaService.streamAnswer(100L, 101L, "stream question");

        assertEquals(SseEmitter.class, emitter.getClass());
        verify(aiAnswerService, timeout(2000)).streamAnswer(eq("This is a parsed document content for stream tests."), eq("stream question"), any());
    }

    @Test
    void shouldThrowWhenStreamQuestionIsBlank() {
        DocumentQaServiceImpl documentQaService = buildService();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> documentQaService.streamAnswer(100L, 101L, "   "));

        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
    }

    @Test
    void shouldListQaHistoryByUserAndDocument() {
        DocumentQaServiceImpl documentQaService = buildService();

        Document document = new Document();
        document.setId(101L);
        document.setUserId(100L);
        when(documentMapper.selectById(101L)).thenReturn(document);

        DocumentQaHistoryItemResponse history = new DocumentQaHistoryItemResponse();
        history.setId(1L);
        history.setDocumentId(101L);
        history.setQuestion("q1");
        history.setAnswer("a1");
        when(documentQaHistoryMapper.selectRecentByUserAndDocument(100L, 101L, 10)).thenReturn(List.of(history));

        List<DocumentQaHistoryItemResponse> result = documentQaService.listHistory(100L, 101L, null);

        assertEquals(1, result.size());
        assertEquals("q1", result.get(0).getQuestion());
    }

    @Test
    void shouldRejectHistoryQueryWhenDocumentBelongsToAnotherUser() {
        DocumentQaServiceImpl documentQaService = buildService();

        Document document = new Document();
        document.setId(101L);
        document.setUserId(200L);
        when(documentMapper.selectById(101L)).thenReturn(document);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> documentQaService.listHistory(100L, 101L, 20));

        assertEquals(ErrorCode.DOCUMENT_FORBIDDEN, ex.getErrorCode());
        verify(documentQaHistoryMapper, never()).selectRecentByUserAndDocument(any(), any(), any(Integer.class));
    }

    @Test
    void shouldRejectAnswerWhenQaRateLimitExceeded() {
        DocumentQaServiceImpl documentQaService = buildService();
        when(redisTokenBucketRateLimiter.tryConsume(
                CommonConstants.buildAiQaRateLimitKey(100L),
                CommonConstants.AI_QA_TOKEN_BUCKET_CAPACITY,
                CommonConstants.AI_QA_TOKEN_BUCKET_REFILL_TOKENS,
                CommonConstants.AI_QA_TOKEN_BUCKET_REFILL_INTERVAL_SECONDS
        )).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> documentQaService.answer(100L, 101L, "question"));

        assertEquals(ErrorCode.RATE_LIMIT_EXCEEDED, ex.getErrorCode());
        assertEquals("问答请求过于频繁，请稍后再试", ex.getMessage());
    }

    @Test
    void shouldRejectStreamWhenQaRateLimitExceeded() {
        DocumentQaServiceImpl documentQaService = buildService();
        when(redisTokenBucketRateLimiter.tryConsume(
                CommonConstants.buildAiQaRateLimitKey(100L),
                CommonConstants.AI_QA_TOKEN_BUCKET_CAPACITY,
                CommonConstants.AI_QA_TOKEN_BUCKET_REFILL_TOKENS,
                CommonConstants.AI_QA_TOKEN_BUCKET_REFILL_INTERVAL_SECONDS
        )).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> documentQaService.streamAnswer(100L, 101L, "question"));

        assertEquals(ErrorCode.RATE_LIMIT_EXCEEDED, ex.getErrorCode());
        assertEquals("问答请求过于频繁，请稍后再试", ex.getMessage());
    }

    @Test
    void shouldRetryAnswerWhenRetryableExceptionThenSucceed() {
        DocumentQaServiceImpl documentQaService = buildService();

        Document document = new Document();
        document.setId(101L);
        document.setUserId(100L);
        document.setContent("retry context");
        when(documentMapper.selectById(101L)).thenReturn(document);
        when(aiAnswerService.answer("retry context", "question"))
                .thenThrow(new IllegalStateException("timeout from model"))
                .thenReturn("最终成功");
        when(documentQaHistoryMapper.insert(any(DocumentQaHistory.class))).thenReturn(1);

        DocumentQaResponse response = documentQaService.answer(100L, 101L, "question");

        assertEquals("最终成功", response.getAnswer());
        verify(aiAnswerService, org.mockito.Mockito.times(2)).answer("retry context", "question");
    }

    @Test
    void shouldRetryAnswerWhenAiStatusIs429ThenSucceed() {
        DocumentQaServiceImpl documentQaService = buildService();

        Document document = new Document();
        document.setId(101L);
        document.setUserId(100L);
        document.setContent("retry context");
        when(documentMapper.selectById(101L)).thenReturn(document);
        when(aiAnswerService.answer("retry context", "question"))
                .thenThrow(new AiRetryableException("真实模型触发限流，status=429"))
                .thenReturn("ok");
        when(documentQaHistoryMapper.insert(any(DocumentQaHistory.class))).thenReturn(1);

        DocumentQaResponse response = documentQaService.answer(100L, 101L, "question");

        assertEquals("ok", response.getAnswer());
        verify(aiAnswerService, org.mockito.Mockito.times(2)).answer("retry context", "question");
    }

    @Test
    void shouldNotRetryWhenAnswerExceptionIsNonRetryable() {
        DocumentQaServiceImpl documentQaService = buildService();

        Document document = new Document();
        document.setId(101L);
        document.setUserId(100L);
        document.setContent("context");
        when(documentMapper.selectById(101L)).thenReturn(document);
        when(aiAnswerService.answer("context", "question"))
                .thenThrow(new IllegalArgumentException("bad input"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> documentQaService.answer(100L, 101L, "question"));

        assertEquals(ErrorCode.AI_CALL_FAILED, ex.getErrorCode());
        verify(aiAnswerService, org.mockito.Mockito.times(1)).answer("context", "question");
    }

    @Test
    void shouldNotRetryWhenAiStatusIs401() {
        DocumentQaServiceImpl documentQaService = buildService();

        Document document = new Document();
        document.setId(101L);
        document.setUserId(100L);
        document.setContent("context");
        when(documentMapper.selectById(101L)).thenReturn(document);
        when(aiAnswerService.answer("context", "question"))
                .thenThrow(new AiNonRetryableException("真实模型鉴权失败，status=401"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> documentQaService.answer(100L, 101L, "question"));

        assertEquals(ErrorCode.AI_CALL_FAILED, ex.getErrorCode());
        verify(aiAnswerService, org.mockito.Mockito.times(1)).answer("context", "question");
    }

    @Test
    void shouldRetryStreamWhenRetryableExceptionThenSucceed() {
        DocumentQaServiceImpl documentQaService = buildService();

        Document document = new Document();
        document.setId(101L);
        document.setUserId(100L);
        document.setContent("stream retry context");
        when(documentMapper.selectById(101L)).thenReturn(document);

        final int[] callCounter = {0};
        doAnswer(invocation -> {
            callCounter[0]++;
            if (callCounter[0] == 1) {
                throw new IllegalStateException("temporary network error");
            }
            Consumer<String> consumer = invocation.getArgument(2);
            consumer.accept("chunk");
            return null;
        }).when(aiAnswerService).streamAnswer(any(), eq("stream question"), any());

        documentQaService.streamAnswer(100L, 101L, "stream question");

        verify(aiAnswerService, timeout(2000).times(2)).streamAnswer(any(), eq("stream question"), any());
    }

    @Test
    void shouldUseCachedAnswerWhenCacheHit() {
        DocumentQaServiceImpl documentQaService = buildService();

        Document document = new Document();
        document.setId(101L);
        document.setUserId(100L);
        document.setContent("cached context");
        LocalDateTime version = LocalDateTime.of(2026, 4, 7, 12, 0, 0);
        document.setUpdateTime(version);
        when(documentMapper.selectById(101L)).thenReturn(document);
        when(documentQaHistoryMapper.insert(any(DocumentQaHistory.class))).thenReturn(1);

        String expectedKey = CommonConstants.buildQaAnswerCacheKey(
                100L,
                101L,
                version.toString(),
                sha256Hex(CommonConstants.QA_DEFAULT_SESSION_ID + "|cache question")
        );
        when(valueOperations.get(expectedKey)).thenReturn("缓存答案");

        DocumentQaResponse response = documentQaService.answer(100L, 101L, "cache question");

        assertEquals("缓存答案", response.getAnswer());
        verify(aiAnswerService, never()).answer(any(), any());
    }

    @Test
    void shouldWriteAnswerToCacheWhenCacheMiss() {
        DocumentQaServiceImpl documentQaService = buildService();

        Document document = new Document();
        document.setId(101L);
        document.setUserId(100L);
        document.setContent("cache miss context");
        LocalDateTime version = LocalDateTime.of(2026, 4, 7, 13, 0, 0);
        document.setUpdateTime(version);
        when(documentMapper.selectById(101L)).thenReturn(document);
        when(aiAnswerService.answer("cache miss context", "cache miss question")).thenReturn("新答案");
        when(documentQaHistoryMapper.insert(any(DocumentQaHistory.class))).thenReturn(1);

        DocumentQaResponse response = documentQaService.answer(100L, 101L, "cache miss question");

        assertEquals("新答案", response.getAnswer());
        String expectedKey = CommonConstants.buildQaAnswerCacheKey(
                100L,
                101L,
                version.toString(),
                sha256Hex(CommonConstants.QA_DEFAULT_SESSION_ID + "|cache miss question")
        );
        verify(valueOperations).set(
                eq(expectedKey),
                eq("新答案"),
                eq(CommonConstants.QA_ANSWER_CACHE_TTL_SECONDS),
                eq(TimeUnit.SECONDS)
        );
    }

    @Test
    void shouldIncludeDocumentVersionInCacheKeyToAvoidStaleAnswer() {
        String keyA = CommonConstants.buildQaAnswerCacheKey(
                100L,
                101L,
                "2026-04-07T12:00",
                sha256Hex(CommonConstants.QA_DEFAULT_SESSION_ID + "|q")
        );
        String keyB = CommonConstants.buildQaAnswerCacheKey(
                100L,
                101L,
                "2026-04-07T12:01",
                sha256Hex(CommonConstants.QA_DEFAULT_SESSION_ID + "|q")
        );

        assertTrue(!keyA.equals(keyB));
    }

    @Test
    void shouldInjectSessionContextWhenSessionIdProvided() {
        DocumentQaServiceImpl documentQaService = buildService();

        Document document = new Document();
        document.setId(101L);
        document.setUserId(100L);
        document.setContent("会话上下文测试文档内容");
        when(documentMapper.selectById(101L)).thenReturn(document);
        when(aiAnswerService.answer(eq("会话上下文测试文档内容"), anyString())).thenReturn("基于会话上下文的回答");
        when(documentQaHistoryMapper.insert(any(DocumentQaHistory.class))).thenReturn(1);

        String sessionKey = CommonConstants.buildQaSessionContextKey(100L, 101L, "sess-1");
        when(listOperations.range(sessionKey, -CommonConstants.QA_SESSION_MAX_CONTEXT_TURNS, -1))
                .thenReturn(List.of("{\"question\":\"上一轮问什么\",\"answer\":\"上一轮答什么\",\"createTime\":\"2026-04-14T10:00:00Z\"}"));

        DocumentQaResponse response = documentQaService.answer(100L, 101L, "这一轮问什么", "sess-1");

        assertEquals("sess-1", response.getSessionId());
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(aiAnswerService).answer(eq("会话上下文测试文档内容"), promptCaptor.capture());
        assertTrue(promptCaptor.getValue().contains("上一轮问什么"));
        assertTrue(promptCaptor.getValue().contains("当前问题：这一轮问什么"));
        verify(listOperations).rightPush(eq(sessionKey), anyString());
        verify(listOperations).trim(sessionKey, -CommonConstants.QA_SESSION_MAX_CONTEXT_TURNS, -1);
        verify(stringRedisTemplate).expire(sessionKey, CommonConstants.QA_SESSION_CONTEXT_TTL_SECONDS, TimeUnit.SECONDS);
    }

    @Test
    void shouldUseDefaultSessionIdWhenSessionIdIsBlank() {
        DocumentQaServiceImpl documentQaService = buildService();

        Document document = new Document();
        document.setId(101L);
        document.setUserId(100L);
        document.setContent("default session context");
        when(documentMapper.selectById(101L)).thenReturn(document);
        when(aiAnswerService.answer("default session context", "question")).thenReturn("ok");
        when(documentQaHistoryMapper.insert(any(DocumentQaHistory.class))).thenReturn(1);

        DocumentQaResponse response = documentQaService.answer(100L, 101L, "question", "  ");

        assertEquals(CommonConstants.QA_DEFAULT_SESSION_ID, response.getSessionId());
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}

