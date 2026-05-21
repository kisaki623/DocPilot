package com.docpilot.backend.ai.rag;

import com.docpilot.backend.ai.agent.tool.DocumentRagTool;
import com.docpilot.backend.ai.entity.DocumentQaHistory;
import com.docpilot.backend.ai.mapper.DocumentQaHistoryMapper;
import com.docpilot.backend.ai.service.AiAnswerService;
import com.docpilot.backend.ai.service.impl.AiRetryExecutor;
import com.docpilot.backend.ai.service.impl.DocumentQaServiceImpl;
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
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentRagQaConsistencyTest {

    private static final long USER_ID = 100L;
    private static final long DOCUMENT_ID = 61L;
    private static final String PRIVATE_DOC_MARKER = "PRIVATE_AGENT_RAG_QA_DOC_MARKER";
    private static final String QUESTION = "Where is DocPilot cache state stored?";
    private static final String DOCUMENT_TEXT = """
            DocPilot stores cache state in Redis and keeps rate limit counters in the token bucket path.
            RocketMQ outbox dispatches parser tasks after upload.
            MinIO stores uploaded objects for parsed documents.
            """ + PRIVATE_DOC_MARKER;

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
    void shouldAlignAgentRagToolAndQaRagContextRetrievalBoundaries() {
        DocumentRagTool agentTool = new DocumentRagTool();
        RagQaContext qaContext = new RagQaContextBuilder().build(DOCUMENT_ID, QUESTION, DOCUMENT_TEXT, 2, 800);

        DocumentRagTool.RagResult agentResult = agentTool.execute(new DocumentRagTool.RagInput(
                DOCUMENT_ID,
                QUESTION,
                DOCUMENT_TEXT,
                2
        ));

        assertThat(agentResult.topK()).isEqualTo(2);
        assertThat(agentResult.retrievedChunks()).hasSize(qaContext.retrievedCount());
        assertThat(agentResult.citations()).hasSameSizeAs(qaContext.citations());
        assertThat(agentResult.answerContext()).isNotBlank();
        assertThat(qaContext.used()).isTrue();
        assertThat(qaContext.trace().contextHashPresent()).isTrue();
        assertThat(agentResult.outputSummary())
                .contains("embeddingProvider=fake")
                .contains("vectorStoreType=in_memory")
                .contains("topK=2")
                .contains("contextHashPresent=true")
                .contains("fallbackUsed=false");
        assertThat(agentResult.outputSummary()).doesNotContain(PRIVATE_DOC_MARKER);
    }

    @Test
    void shouldKeepVectorStoreUserAndDocumentScopeIsolated() {
        FakeEmbeddingModel embeddingModel = new FakeEmbeddingModel(64);
        InMemoryVectorStore vectorStore = new InMemoryVectorStore();
        vectorStore.add(RagSearchScope.of("user-a", 9001L),
                chunk(9001L, 0, "user-a cache isolation marker"), embeddingModel.embed("user-a cache isolation marker"));
        vectorStore.add(RagSearchScope.of("user-b", 9001L),
                chunk(9001L, 1, "user-b cache isolation marker"), embeddingModel.embed("user-b cache isolation marker"));
        vectorStore.add(RagSearchScope.of("user-a", 9002L),
                chunk(9002L, 0, "other document isolation marker"), embeddingModel.embed("other document isolation marker"));

        List<VectorSearchResult> userAHits = vectorStore.searchTopK(
                RagSearchScope.of("user-a", 9001L),
                embeddingModel.embed("cache isolation marker"),
                5
        );
        List<VectorSearchResult> userBHits = vectorStore.searchTopK(
                RagSearchScope.of("user-b", 9001L),
                embeddingModel.embed("cache isolation marker"),
                5
        );

        assertThat(userAHits).hasSize(1);
        assertThat(userAHits.get(0).chunk().documentId()).isEqualTo(9001L);
        assertThat(userAHits.get(0).chunk().text()).contains("user-a").doesNotContain("user-b");
        assertThat(userBHits).hasSize(1);
        assertThat(userBHits.get(0).chunk().text()).contains("user-b").doesNotContain("user-a");
    }

    @Test
    void shouldFallbackFriendlyForAgentRagToolAndQaRagFailure() {
        RagVectorStoreProperties vectorStoreProperties = new RagVectorStoreProperties();
        vectorStoreProperties.setProvider("qdrant_disabled");
        DocumentRagTool failingAgentTool = new DocumentRagTool(
                new EmbeddingModelFactory(),
                new RagEmbeddingProperties(),
                new InMemoryVectorStore(),
                new RagIndexManager(),
                vectorStoreProperties,
                new VectorStoreFactory()
        );
        DocumentRagTool.RagResult agentResult = failingAgentTool.execute(new DocumentRagTool.RagInput(
                DOCUMENT_ID,
                QUESTION,
                DOCUMENT_TEXT,
                2
        ));

        RagQaProperties properties = enabledRagProperties(2, 300);
        DocumentQaServiceImpl qaService = buildService(properties, new ThrowingRagQaContextBuilder());
        when(documentMapper.selectById(DOCUMENT_ID)).thenReturn(document(LocalDateTime.of(2026, 5, 21, 10, 0)));
        when(aiAnswerService.answer(anyString(), eq(QUESTION))).thenReturn("plain fallback answer");
        when(documentQaHistoryMapper.insert(any(DocumentQaHistory.class))).thenReturn(1);

        qaService.answer(USER_ID, DOCUMENT_ID, QUESTION);

        ArgumentCaptor<String> contextCaptor = ArgumentCaptor.forClass(String.class);
        verify(aiAnswerService).answer(contextCaptor.capture(), eq(QUESTION));
        assertThat(agentResult.retrievedChunks()).isEmpty();
        assertThat(agentResult.outputSummary())
                .contains("fallbackUsed=true")
                .contains("fallbackReason=qdrant_disabled");
        assertThat(contextCaptor.getValue()).doesNotStartWith("RAG context:");
        assertThat(contextCaptor.getValue()).contains("Redis");
        assertThat(agentResult.outputSummary()).doesNotContain(PRIVATE_DOC_MARKER);
    }

    @Test
    void shouldKeepPlainQaUnchangedWhenRagFlagDisabled() {
        CountingRagQaContextBuilder ragBuilder = new CountingRagQaContextBuilder(RagQaContext.empty());
        DocumentQaServiceImpl qaService = buildService(new RagQaProperties(), ragBuilder);
        when(documentMapper.selectById(DOCUMENT_ID)).thenReturn(document(LocalDateTime.of(2026, 5, 21, 11, 0)));
        when(aiAnswerService.answer(anyString(), eq(QUESTION))).thenReturn("plain answer");
        when(documentQaHistoryMapper.insert(any(DocumentQaHistory.class))).thenReturn(1);

        qaService.answer(USER_ID, DOCUMENT_ID, QUESTION);

        ArgumentCaptor<String> contextCaptor = ArgumentCaptor.forClass(String.class);
        verify(aiAnswerService).answer(contextCaptor.capture(), eq(QUESTION));
        assertThat(ragBuilder.callCount()).isZero();
        assertThat(contextCaptor.getValue()).doesNotStartWith("RAG context:");
        assertThat(contextCaptor.getValue()).contains("Redis");
    }

    @Test
    void shouldMakeQaCacheKeyRagAwareWhenRagFlagEnabled() {
        String ragContextText = "[1] sanitized cache context";
        LocalDateTime version = LocalDateTime.of(2026, 5, 21, 12, 0);
        RagQaProperties properties = enabledRagProperties(2, 64);
        DocumentQaServiceImpl qaService = buildService(properties, new StubRagQaContextBuilder(new RagQaContext(
                true,
                ragContextText,
                List.of(),
                1,
                1,
                RagQaTrace.retrieval("fake", true, 2, 1, 64, ragContextText.length(), false, true, 0)
        )));
        when(documentMapper.selectById(DOCUMENT_ID)).thenReturn(document(version));
        when(aiAnswerService.answer("RAG context:\n" + ragContextText, QUESTION)).thenReturn("rag answer");
        when(documentQaHistoryMapper.insert(any(DocumentQaHistory.class))).thenReturn(1);

        qaService.answer(USER_ID, DOCUMENT_ID, QUESTION);

        ArgumentCaptor<String> cacheKeyCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(
                cacheKeyCaptor.capture(),
                eq("rag answer"),
                eq(CommonConstants.QA_ANSWER_CACHE_TTL_SECONDS),
                eq(TimeUnit.SECONDS)
        );
        String ragVariant = "rag:topK=2:maxContextChars=64:contextHash=" + sha256Hex(ragContextText);
        String expectedCacheKey = CommonConstants.buildQaAnswerCacheKey(
                USER_ID,
                DOCUMENT_ID,
                version.toString(),
                sha256Hex(QUESTION + "\n" + ragVariant)
        );
        assertThat(cacheKeyCaptor.getValue()).isEqualTo(expectedCacheKey);
        assertThat(cacheKeyCaptor.getValue()).doesNotContain(ragContextText);
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

    private DocumentChunk chunk(Long documentId, int chunkIndex, String text) {
        return new DocumentChunk(documentId, chunkIndex, text, Map.of("contentHash", "hash-" + documentId + "-" + chunkIndex));
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
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

        private int callCount() {
            return callCount;
        }
    }

    private static class ThrowingRagQaContextBuilder extends RagQaContextBuilder {

        @Override
        public RagQaContext build(Long documentId, String question, String documentText, int topK, int maxContextChars) {
            throw new IllegalStateException("qdrant vector store is disabled");
        }
    }
}
