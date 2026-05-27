package com.docpilot.backend.ai.service;

import com.docpilot.backend.ai.mapper.DocumentQaHistoryMapper;
import com.docpilot.backend.ai.rag.RagCitation;
import com.docpilot.backend.ai.rag.EmbeddingModelFactory;
import com.docpilot.backend.ai.rag.InMemoryVectorStore;
import com.docpilot.backend.ai.rag.RagEmbeddingProperties;
import com.docpilot.backend.ai.rag.RagIndexManager;
import com.docpilot.backend.ai.rag.RagQaContext;
import com.docpilot.backend.ai.rag.RagQaContextBuilder;
import com.docpilot.backend.ai.rag.RagQaProperties;
import com.docpilot.backend.ai.rag.RagVectorStoreProperties;
import com.docpilot.backend.ai.rag.VectorStoreFactory;
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

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
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
import java.util.Map;

@ExtendWith(MockitoExtension.class)
class DocumentQaServiceImplTest {

    private HttpServer qdrantServer;

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

    @AfterEach
    void tearDownQdrantServer() {
        if (qdrantServer != null) {
            qdrantServer.stop(0);
            qdrantServer = null;
        }
    }

    private DocumentQaServiceImpl buildService() {
        return buildService(new RagQaProperties(), new RagQaContextBuilder());
    }

    private DocumentQaServiceImpl buildService(RagQaProperties ragQaProperties, RagQaContextBuilder ragQaContextBuilder) {
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
                aiRetryExecutor,
                ragQaProperties,
                ragQaContextBuilder
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
        assertTrue(citation.getSnippet().contains("引用定位"));
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
    void shouldUseRagContextWhenQaRagFlagEnabled() {
        RagQaProperties ragQaProperties = new RagQaProperties();
        ragQaProperties.setEnabled(true);
        ragQaProperties.setTopK(2);
        ragQaProperties.setMaxContextChars(128);
        RagQaContextBuilder ragQaContextBuilder = new StubRagQaContextBuilder(new RagQaContext(
                true,
                "[1] documentId=101, chunkIndex=0, score=0.9000\nRedis cache context",
                List.of(new RagCitation(101L, 0, 0.9D, Map.of("charStart", "0", "charEnd", "20"))),
                1,
                1
        ));
        DocumentQaServiceImpl documentQaService = buildService(ragQaProperties, ragQaContextBuilder);

        Document document = new Document();
        document.setId(101L);
        document.setUserId(100L);
        document.setContent("Full document text should only be used for citations when RAG context is available.");
        when(documentMapper.selectById(101L)).thenReturn(document);
        when(aiAnswerService.answer(eq("RAG context:\n[1] documentId=101, chunkIndex=0, score=0.9000\nRedis cache context"),
                eq("How is Redis cache used?"))).thenReturn("RAG answer");
        when(documentQaHistoryMapper.insert(any(DocumentQaHistory.class))).thenReturn(1);

        DocumentQaResponse response = documentQaService.answer(100L, 101L, "How is Redis cache used?");

        assertEquals("RAG answer", response.getAnswer());
        verify(aiAnswerService).answer(eq("RAG context:\n[1] documentId=101, chunkIndex=0, score=0.9000\nRedis cache context"),
                eq("How is Redis cache used?"));
    }

    @Test
    void shouldKeepPlainQaWhenQaRagFlagDisabled() {
        RagQaProperties ragQaProperties = new RagQaProperties();
        ragQaProperties.setEnabled(false);
        CountingRagQaContextBuilder ragQaContextBuilder = new CountingRagQaContextBuilder(RagQaContext.empty());
        DocumentQaServiceImpl documentQaService = buildService(ragQaProperties, ragQaContextBuilder);

        Document document = new Document();
        document.setId(101L);
        document.setUserId(100L);
        document.setContent("plain qa context");
        when(documentMapper.selectById(101L)).thenReturn(document);
        when(aiAnswerService.answer("plain qa context", "plain question")).thenReturn("plain answer");
        when(documentQaHistoryMapper.insert(any(DocumentQaHistory.class))).thenReturn(1);

        DocumentQaResponse response = documentQaService.answer(100L, 101L, "plain question");

        assertEquals("plain answer", response.getAnswer());
        assertEquals(0, ragQaContextBuilder.callCount);
        verify(aiAnswerService).answer("plain qa context", "plain question");
    }

    @Test
    void shouldFallbackToPlainQaWhenRagContextBuilderThrows() {
        RagQaProperties ragQaProperties = enabledRagProperties(3, 200);
        DocumentQaServiceImpl documentQaService = buildService(ragQaProperties, new ThrowingRagQaContextBuilder());

        Document document = new Document();
        document.setId(101L);
        document.setUserId(100L);
        document.setContent("fallback plain context");
        when(documentMapper.selectById(101L)).thenReturn(document);
        when(aiAnswerService.answer("fallback plain context", "fallback question")).thenReturn("fallback answer");
        when(documentQaHistoryMapper.insert(any(DocumentQaHistory.class))).thenReturn(1);

        DocumentQaResponse response = documentQaService.answer(100L, 101L, "fallback question");

        assertEquals("fallback answer", response.getAnswer());
        verify(aiAnswerService).answer("fallback plain context", "fallback question");
    }

    @Test
    void shouldFallbackToPlainQaAndPlainCacheKeyWhenQdrantReturnsHttpError() throws Exception {
        startFailingQdrantServer(500);
        RagQaProperties ragQaProperties = enabledRagProperties(3, 200);
        DocumentQaServiceImpl documentQaService = buildService(ragQaProperties, qdrantRagQaContextBuilder());
        LocalDateTime version = LocalDateTime.of(2026, 5, 21, 10, 30, 0);

        Document document = new Document();
        document.setId(101L);
        document.setUserId(100L);
        document.setContent("qdrant failure should keep plain QA context");
        document.setUpdateTime(version);
        when(documentMapper.selectById(101L)).thenReturn(document);
        when(aiAnswerService.answer("qdrant failure should keep plain QA context", "qdrant fallback question"))
                .thenReturn("plain fallback answer");
        when(documentQaHistoryMapper.insert(any(DocumentQaHistory.class))).thenReturn(1);

        DocumentQaResponse response = documentQaService.answer(100L, 101L, "qdrant fallback question");

        String expectedPlainKey = CommonConstants.buildQaAnswerCacheKey(
                100L,
                101L,
                version.toString(),
                sha256Hex("qdrant fallback question")
        );
        assertEquals("plain fallback answer", response.getAnswer());
        verify(aiAnswerService).answer("qdrant failure should keep plain QA context", "qdrant fallback question");
        verify(valueOperations).set(
                eq(expectedPlainKey),
                eq("plain fallback answer"),
                eq(CommonConstants.QA_ANSWER_CACHE_TTL_SECONDS),
                eq(TimeUnit.SECONDS)
        );
    }

    @Test
    void shouldFallbackToPlainQaWhenRagContextEmpty() {
        RagQaProperties ragQaProperties = enabledRagProperties(3, 200);
        DocumentQaServiceImpl documentQaService = buildService(
                ragQaProperties,
                new StubRagQaContextBuilder(RagQaContext.empty())
        );

        Document document = new Document();
        document.setId(101L);
        document.setUserId(100L);
        document.setContent("empty recall fallback context");
        when(documentMapper.selectById(101L)).thenReturn(document);
        when(aiAnswerService.answer("empty recall fallback context", "empty recall question")).thenReturn("plain answer");
        when(documentQaHistoryMapper.insert(any(DocumentQaHistory.class))).thenReturn(1);

        DocumentQaResponse response = documentQaService.answer(100L, 101L, "empty recall question");

        assertEquals("plain answer", response.getAnswer());
        verify(aiAnswerService).answer("empty recall fallback context", "empty recall question");
    }

    @Test
    void shouldPassConfiguredTopKAndMaxContextCharsToRagBuilder() {
        RagQaProperties ragQaProperties = enabledRagProperties(2, 64);
        RecordingRagQaContextBuilder ragQaContextBuilder = new RecordingRagQaContextBuilder(new RagQaContext(
                true,
                "[1] configured RAG context",
                List.of(),
                1,
                1
        ));
        DocumentQaServiceImpl documentQaService = buildService(ragQaProperties, ragQaContextBuilder);

        Document document = new Document();
        document.setId(101L);
        document.setUserId(100L);
        document.setContent("configured context");
        when(documentMapper.selectById(101L)).thenReturn(document);
        when(aiAnswerService.answer("RAG context:\n[1] configured RAG context", "configured question"))
                .thenReturn("configured answer");
        when(documentQaHistoryMapper.insert(any(DocumentQaHistory.class))).thenReturn(1);

        documentQaService.answer(100L, 101L, "configured question");

        assertEquals(2, ragQaContextBuilder.topK);
        assertEquals(64, ragQaContextBuilder.maxContextChars);
    }

    @Test
    void shouldIncludeRagVariantInAnswerCacheKeyWhenRagUsed() {
        RagQaProperties ragQaProperties = enabledRagProperties(2, 64);
        String ragContextText = "[1] cache RAG context";
        DocumentQaServiceImpl documentQaService = buildService(
                ragQaProperties,
                new StubRagQaContextBuilder(new RagQaContext(true, ragContextText, List.of(), 1, 1))
        );

        Document document = new Document();
        document.setId(101L);
        document.setUserId(100L);
        document.setContent("cache source context");
        LocalDateTime version = LocalDateTime.of(2026, 5, 20, 12, 0, 0);
        document.setUpdateTime(version);
        when(documentMapper.selectById(101L)).thenReturn(document);
        when(aiAnswerService.answer("RAG context:\n" + ragContextText, "cache rag question")).thenReturn("cache rag answer");
        when(documentQaHistoryMapper.insert(any(DocumentQaHistory.class))).thenReturn(1);

        documentQaService.answer(100L, 101L, "cache rag question");

        String ragVariant = "rag:topK=2:maxContextChars=64:contextHash=" + sha256Hex(ragContextText);
        String expectedKey = CommonConstants.buildQaAnswerCacheKey(
                100L,
                101L,
                version.toString(),
                sha256Hex("cache rag question\n" + ragVariant)
        );
        verify(valueOperations).set(
                eq(expectedKey),
                eq("cache rag answer"),
                eq(CommonConstants.QA_ANSWER_CACHE_TTL_SECONDS),
                eq(TimeUnit.SECONDS)
        );
    }

    @Test
    void shouldStreamWithRagContextWhenQaRagFlagEnabled() {
        RagQaProperties ragQaProperties = enabledRagProperties(1, 80);
        DocumentQaServiceImpl documentQaService = buildService(
                ragQaProperties,
                new StubRagQaContextBuilder(new RagQaContext(true, "[1] stream RAG context", List.of(), 1, 1))
        );

        Document document = new Document();
        document.setId(101L);
        document.setUserId(100L);
        document.setContent("stream source context");
        when(documentMapper.selectById(101L)).thenReturn(document);
        when(documentQaHistoryMapper.insert(any(DocumentQaHistory.class))).thenReturn(1);
        doAnswer(invocation -> {
            Consumer<String> consumer = invocation.getArgument(2);
            consumer.accept("stream rag answer");
            return null;
        }).when(aiAnswerService).streamAnswer(eq("RAG context:\n[1] stream RAG context"), eq("stream rag question"), any());

        documentQaService.streamAnswer(100L, 101L, "stream rag question");

        verify(aiAnswerService, timeout(2000)).streamAnswer(
                eq("RAG context:\n[1] stream RAG context"),
                eq("stream rag question"),
                any()
        );
    }

    @Test
    void shouldNotBuildRagContextWhenRateLimited() {
        RagQaProperties ragQaProperties = enabledRagProperties(3, 200);
        CountingRagQaContextBuilder ragQaContextBuilder = new CountingRagQaContextBuilder(RagQaContext.empty());
        DocumentQaServiceImpl documentQaService = buildService(ragQaProperties, ragQaContextBuilder);
        when(redisTokenBucketRateLimiter.tryConsume(
                CommonConstants.buildAiQaRateLimitKey(100L),
                CommonConstants.AI_QA_TOKEN_BUCKET_CAPACITY,
                CommonConstants.AI_QA_TOKEN_BUCKET_REFILL_TOKENS,
                CommonConstants.AI_QA_TOKEN_BUCKET_REFILL_INTERVAL_SECONDS
        )).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> documentQaService.answer(100L, 101L, "rate limited question"));

        assertEquals(ErrorCode.RATE_LIMIT_EXCEEDED, ex.getErrorCode());
        assertEquals(0, ragQaContextBuilder.callCount);
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
    void shouldUseCachedAnswerWhenStreamCacheHit() {
        DocumentQaServiceImpl documentQaService = buildService();

        Document document = new Document();
        document.setId(101L);
        document.setUserId(100L);
        document.setContent("stream cached context");
        LocalDateTime version = LocalDateTime.of(2026, 4, 8, 9, 0, 0);
        document.setUpdateTime(version);
        when(documentMapper.selectById(101L)).thenReturn(document);
        when(documentQaHistoryMapper.insert(any(DocumentQaHistory.class))).thenReturn(1);

        String expectedKey = CommonConstants.buildQaAnswerCacheKey(
                100L,
                101L,
                version.toString(),
                sha256Hex("stream question")
        );
        when(valueOperations.get(expectedKey)).thenReturn("缓存流式答案");

        documentQaService.streamAnswer(100L, 101L, "stream question");

        verify(documentQaHistoryMapper, timeout(2000)).insert(any(DocumentQaHistory.class));
        verify(aiAnswerService, never()).streamAnswer(any(), any(), any());
    }

    @Test
    void shouldWriteAnswerToCacheAfterStreamGeneration() {
        DocumentQaServiceImpl documentQaService = buildService();

        Document document = new Document();
        document.setId(101L);
        document.setUserId(100L);
        document.setContent("stream cache miss context");
        LocalDateTime version = LocalDateTime.of(2026, 4, 8, 10, 0, 0);
        document.setUpdateTime(version);
        when(documentMapper.selectById(101L)).thenReturn(document);
        when(documentQaHistoryMapper.insert(any(DocumentQaHistory.class))).thenReturn(1);

        doAnswer(invocation -> {
            Consumer<String> consumer = invocation.getArgument(2);
            consumer.accept("stream");
            consumer.accept(" answer");
            return null;
        }).when(aiAnswerService).streamAnswer(eq("stream cache miss context"), eq("stream cache miss question"), any());

        documentQaService.streamAnswer(100L, 101L, "stream cache miss question");

        String expectedKey = CommonConstants.buildQaAnswerCacheKey(
                100L,
                101L,
                version.toString(),
                sha256Hex("stream cache miss question")
        );
        verify(valueOperations, timeout(2000)).set(
                eq(expectedKey),
                eq("stream answer"),
                eq(CommonConstants.QA_ANSWER_CACHE_TTL_SECONDS),
                eq(TimeUnit.SECONDS)
        );
    }

    @Test
    void shouldThrowWhenStreamQuestionIsBlank() {
        DocumentQaServiceImpl documentQaService = buildService();
        SseEmitter emitter = documentQaService.streamAnswer(100L, 101L, "   ");
        assertNotNull(emitter);
        verify(aiAnswerService, never()).streamAnswer(any(), any(), any());
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
        SseEmitter emitter = documentQaService.streamAnswer(100L, 101L, "question");
        assertNotNull(emitter);
        verify(redisTokenBucketRateLimiter, timeout(2000)).tryConsume(
                CommonConstants.buildAiQaRateLimitKey(100L),
                CommonConstants.AI_QA_TOKEN_BUCKET_CAPACITY,
                CommonConstants.AI_QA_TOKEN_BUCKET_REFILL_TOKENS,
                CommonConstants.AI_QA_TOKEN_BUCKET_REFILL_INTERVAL_SECONDS
        );
        verify(aiAnswerService, never()).streamAnswer(any(), any(), any());
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
                sha256Hex("cache question")
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
                sha256Hex("cache miss question")
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
                sha256Hex("q")
        );
        String keyB = CommonConstants.buildQaAnswerCacheKey(
                100L,
                101L,
                "2026-04-07T12:01",
                sha256Hex("q")
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

    private void startFailingQdrantServer(int statusCode) throws IOException {
        qdrantServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        qdrantServer.createContext("/", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] bytes = "{\"status\":\"error\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(statusCode, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        qdrantServer.start();
    }

    private RagQaContextBuilder qdrantRagQaContextBuilder() {
        RagVectorStoreProperties vectorStoreProperties = new RagVectorStoreProperties();
        vectorStoreProperties.setProvider("qdrant");
        RagVectorStoreProperties.Qdrant qdrant = new RagVectorStoreProperties.Qdrant();
        qdrant.setEndpoint("http://127.0.0.1:" + qdrantServer.getAddress().getPort());
        qdrant.setCollection("docpilot_failure");
        qdrant.setConnectTimeoutMs(1000);
        qdrant.setRequestTimeoutMs(3000);
        vectorStoreProperties.setQdrant(qdrant);
        return new RagQaContextBuilder(
                new EmbeddingModelFactory(),
                new RagEmbeddingProperties(),
                new InMemoryVectorStore(),
                new RagIndexManager(),
                vectorStoreProperties,
                new VectorStoreFactory()
        );
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private RagQaProperties enabledRagProperties(int topK, int maxContextChars) {
        RagQaProperties properties = new RagQaProperties();
        properties.setEnabled(true);
        properties.setTopK(topK);
        properties.setMaxContextChars(maxContextChars);
        properties.setFallbackEnabled(true);
        return properties;
    }

    private static class StubRagQaContextBuilder extends RagQaContextBuilder {

        private final RagQaContext context;

        private StubRagQaContextBuilder(RagQaContext context) {
            this.context = context;
        }

        @Override
        public RagQaContext build(Long documentId, String question, String documentText, int topK, int maxContextChars) {
            return context;
        }
    }

    private static class CountingRagQaContextBuilder extends RagQaContextBuilder {

        private final RagQaContext context;
        private int callCount;

        private CountingRagQaContextBuilder(RagQaContext context) {
            this.context = context;
        }

        @Override
        public RagQaContext build(Long documentId, String question, String documentText, int topK, int maxContextChars) {
            callCount++;
            return context;
        }
    }

    private static class RecordingRagQaContextBuilder extends RagQaContextBuilder {

        private final RagQaContext context;
        private int topK;
        private int maxContextChars;

        private RecordingRagQaContextBuilder(RagQaContext context) {
            this.context = context;
        }

        @Override
        public RagQaContext build(Long documentId, String question, String documentText, int topK, int maxContextChars) {
            this.topK = topK;
            this.maxContextChars = maxContextChars;
            return context;
        }
    }

    private static class ThrowingRagQaContextBuilder extends RagQaContextBuilder {

        @Override
        public RagQaContext build(Long documentId, String question, String documentText, int topK, int maxContextChars) {
            throw new IllegalStateException("safe test exception");
        }
    }
}

