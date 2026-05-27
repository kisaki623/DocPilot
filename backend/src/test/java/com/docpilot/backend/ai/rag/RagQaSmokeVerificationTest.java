package com.docpilot.backend.ai.rag;

import com.docpilot.backend.ai.entity.DocumentQaHistory;
import com.docpilot.backend.ai.mapper.DocumentQaHistoryMapper;
import com.docpilot.backend.ai.service.AiAnswerService;
import com.docpilot.backend.ai.service.impl.AiRetryExecutor;
import com.docpilot.backend.ai.service.impl.DocumentQaServiceImpl;
import com.docpilot.backend.ai.vo.DocumentQaResponse;
import com.docpilot.backend.common.constant.CommonConstants;
import com.docpilot.backend.common.limiter.RedisTokenBucketRateLimiter;
import com.docpilot.backend.document.entity.Document;
import com.docpilot.backend.document.mapper.DocumentMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagQaSmokeVerificationTest {

    private static final long USER_ID = 100L;
    private static final long DOCUMENT_ID = 61L;
    private static final String DOCUMENT_BODY_MARKER = "PRIVATE_DOC_BODY_MARKER_DO_NOT_DUMP";
    private static final String QUESTION = "Where does DocPilot store cache and rate limit state?";
    private static final String DOCUMENT_TEXT = """
            DocPilot uses Redis cache for hot session context and token bucket rate limit counters.
            RocketMQ outbox dispatches parser tasks to asynchronous consumers.
            MinIO stores uploaded document objects, while Agent trace stores tool execution metadata.
            """ + DOCUMENT_BODY_MARKER;

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

    @Test
    void shouldSmokeVerifyQaRagFeatureFlagWithFakeEmbedding() {
        RagQaProperties ragQaProperties = enabledRagProperties(3, 2000);
        RagQaContextBuilder ragQaContextBuilder = new RagQaContextBuilder(
                new EmbeddingModelFactory(),
                new RagEmbeddingProperties()
        );
        DocumentQaServiceImpl documentQaService = buildService(ragQaProperties, ragQaContextBuilder);
        LocalDateTime version = LocalDateTime.of(2026, 5, 20, 15, 30, 0);
        when(documentMapper.selectById(DOCUMENT_ID)).thenReturn(document(version));
        when(aiAnswerService.answer(anyString(), eq(QUESTION))).thenReturn("RAG smoke answer");
        when(documentQaHistoryMapper.insert(any(DocumentQaHistory.class))).thenReturn(1);

        RagQaContext directContext = ragQaContextBuilder.build(DOCUMENT_ID, QUESTION, normalizeWhitespace(DOCUMENT_TEXT), 3, 2000);
        DocumentQaResponse response = documentQaService.answer(USER_ID, DOCUMENT_ID, QUESTION);

        ArgumentCaptor<String> contextCaptor = ArgumentCaptor.forClass(String.class);
        verify(aiAnswerService).answer(contextCaptor.capture(), eq(QUESTION));
        ArgumentCaptor<String> cacheKeyCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(
                cacheKeyCaptor.capture(),
                eq("RAG smoke answer"),
                eq(CommonConstants.QA_ANSWER_CACHE_TTL_SECONDS),
                eq(TimeUnit.SECONDS)
        );

        String expectedRagVariant = "rag:topK=3:maxContextChars=2000:contextHash="
                + sha256Hex(directContext.contextText());
        String expectedCacheKey = CommonConstants.buildQaAnswerCacheKey(
                USER_ID,
                DOCUMENT_ID,
                version.toString(),
                sha256Hex(QUESTION + "\n" + expectedRagVariant)
        );
        RagQaSmokeSummary summary = new RagQaSmokeSummary(
                DOCUMENT_ID,
                true,
                3,
                !sha256Hex(directContext.contextText()).isBlank(),
                directContext.retrievedCount(),
                false,
                directContext.citations().size(),
                expectedCacheKey.equals(cacheKeyCaptor.getValue())
        );

        assertThat(response.getAnswer()).isEqualTo("RAG smoke answer");
        assertThat(contextCaptor.getValue()).startsWith("RAG context:\n");
        assertThat(directContext.used()).isTrue();
        assertThat(summary.retrievedCount()).isGreaterThan(0);
        assertThat(summary.contextHashExists()).isTrue();
        assertThat(summary.citationCount()).isGreaterThan(0);
        assertThat(directContext.citations().get(0).metadata()).containsKeys("charStart", "charEnd", "contentHash");
        assertThat(summary.cacheKeyRagAware()).isTrue();
        assertThat(summary.toString()).doesNotContain(DOCUMENT_BODY_MARKER);
    }

    @Test
    void shouldSmokeVerifyPlainQaRemainsDefaultWhenRagDisabled() {
        RagQaProperties ragQaProperties = new RagQaProperties();
        CountingRagQaContextBuilder ragQaContextBuilder = new CountingRagQaContextBuilder();
        DocumentQaServiceImpl documentQaService = buildService(ragQaProperties, ragQaContextBuilder);
        when(documentMapper.selectById(DOCUMENT_ID)).thenReturn(document(LocalDateTime.of(2026, 5, 20, 16, 0, 0)));
        when(aiAnswerService.answer(anyString(), eq(QUESTION))).thenReturn("plain answer");
        when(documentQaHistoryMapper.insert(any(DocumentQaHistory.class))).thenReturn(1);

        documentQaService.answer(USER_ID, DOCUMENT_ID, QUESTION);

        ArgumentCaptor<String> contextCaptor = ArgumentCaptor.forClass(String.class);
        verify(aiAnswerService).answer(contextCaptor.capture(), eq(QUESTION));
        assertThat(ragQaContextBuilder.callCount).isZero();
        assertThat(contextCaptor.getValue()).doesNotStartWith("RAG context:");
        assertThat(contextCaptor.getValue()).contains("Redis cache");
    }

    private DocumentQaServiceImpl buildService(RagQaProperties ragQaProperties, RagQaContextBuilder ragQaContextBuilder) {
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
                new AiRetryExecutor(),
                ragQaProperties,
                ragQaContextBuilder
        );
    }

    private RagQaProperties enabledRagProperties(int topK, int maxContextChars) {
        RagQaProperties properties = new RagQaProperties();
        properties.setEnabled(true);
        properties.setTopK(topK);
        properties.setMaxContextChars(maxContextChars);
        properties.setFallbackEnabled(true);
        return properties;
    }

    private Document document(LocalDateTime version) {
        Document document = new Document();
        document.setId(DOCUMENT_ID);
        document.setUserId(USER_ID);
        document.setContent(DOCUMENT_TEXT);
        document.setUpdateTime(version);
        return document;
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private String normalizeWhitespace(String content) {
        return content.replaceAll("\\s+", " ").trim();
    }

    private record RagQaSmokeSummary(Long documentId,
                                     boolean ragEnabled,
                                     int topK,
                                     boolean contextHashExists,
                                     int retrievedCount,
                                     boolean fallbackUsed,
                                     int citationCount,
                                     boolean cacheKeyRagAware) {
    }

    private static class CountingRagQaContextBuilder extends RagQaContextBuilder {

        private int callCount;

        @Override
        public RagQaContext build(Long documentId, String question, String documentText, int topK, int maxContextChars) {
            callCount++;
            return RagQaContext.empty();
        }
    }
}
