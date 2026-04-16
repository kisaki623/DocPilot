package com.docpilot.backend.benchmark;

import com.docpilot.backend.ai.entity.DocumentQaHistory;
import com.docpilot.backend.ai.mapper.DocumentQaHistoryMapper;
import com.docpilot.backend.ai.service.AiAnswerService;
import com.docpilot.backend.ai.service.impl.AiRetryExecutor;
import com.docpilot.backend.ai.service.impl.DocumentQaServiceImpl;
import com.docpilot.backend.ai.service.impl.MockAiAnswerService;
import com.docpilot.backend.ai.service.impl.RealAiAnswerService;
import com.docpilot.backend.common.constant.CommonConstants;
import com.docpilot.backend.common.constant.ParseStatusConstants;
import com.docpilot.backend.common.limiter.RedisTokenBucketRateLimiter;
import com.docpilot.backend.document.vo.DocumentCreateResponse;
import com.docpilot.backend.document.entity.Document;
import com.docpilot.backend.document.mapper.DocumentMapper;
import com.docpilot.backend.document.service.impl.DocumentServiceImpl;
import com.docpilot.backend.document.vo.DocumentDetailResponse;
import com.docpilot.backend.file.entity.FileRecord;
import com.docpilot.backend.file.mapper.FileRecordMapper;
import com.docpilot.backend.mq.service.ParseTaskOutboxRelayService;
import com.docpilot.backend.task.entity.ParseTask;
import com.docpilot.backend.task.mapper.ParseTaskMapper;
import com.docpilot.backend.task.service.impl.ParseTaskServiceImpl;
import com.docpilot.backend.task.vo.ParseTaskCreateResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class Task11_6BenchmarkTest {

    private static final long USER_ID = 100L;
    private static final long DOCUMENT_ID = 101L;
    private static final int CREATE_WARMUP = 40;
    private static final int CREATE_REQUESTS = 400;
    private static final int CREATE_CONCURRENCY = 10;
    private static final int ASYNC_PROOF_REQUESTS = 240;
    private static final int ASYNC_PROOF_CONCURRENCY = 10;
    private static final int ASYNC_CONSUMER_DELAY_MS = 160;

    @Test
    void shouldGenerateStage11Task11_6BenchmarkArtifacts() throws Exception {
        CacheScenarioResult detailCache = benchmarkDocumentDetailCache();
        CacheScenarioResult qaCache = benchmarkQaAnswerCache();
        AiModeScenarioResult aiMode = benchmarkAiModeComparison();
        CreateMainChainScenarioResult createMainChain = benchmarkCreateMainChain();

        assertTrue(detailCache.hitStats().avgMs() < detailCache.missStats().avgMs(), "detail cache hit should be faster than miss");
        assertTrue(qaCache.hitStats().avgMs() < qaCache.missStats().avgMs(), "qa cache hit should be faster than miss");
        assertTrue(aiMode.mockStats().successRate() >= 99.0D, "mock success rate should be stable");
        assertTrue(aiMode.realStats().successRate() >= 99.0D, "real(local) success rate should be stable");
        assertTrue(createMainChain.documentCreate().stats().successRate() >= 99.0D, "document/create success rate should be stable");
        assertTrue(createMainChain.parseCreate().stats().successRate() >= 99.0D, "task/parse/create success rate should be stable");
        assertTrue(createMainChain.asyncNonBlockingProof().responseBeforeParseFinishRate() >= 95.0D,
                "parse/create should return before async parse finishes in most requests");

        BenchmarkReport report = new BenchmarkReport(detailCache, qaCache, aiMode, createMainChain);
        writeArtifacts(report);
    }

    private CacheScenarioResult benchmarkDocumentDetailCache() {
        RedisHarness redisHarness = new RedisHarness();
        DocumentMapper documentMapper = mock(DocumentMapper.class);
        FileRecordMapper fileRecordMapper = mock(FileRecordMapper.class);

        Document document = new Document();
        document.setId(DOCUMENT_ID);
        document.setUserId(USER_ID);
        document.setParseStatus("SUCCESS");

        DocumentDetailResponse detailResponse = new DocumentDetailResponse();
        detailResponse.setDocumentId(DOCUMENT_ID);
        detailResponse.setFileRecordId(11L);
        detailResponse.setTitle("Benchmark Doc");
        detailResponse.setFileName("benchmark.md");
        detailResponse.setFileType("md");
        detailResponse.setParseStatus("SUCCESS");
        detailResponse.setSummary("summary");
        detailResponse.setContent("content");
        detailResponse.setCreateTime(LocalDateTime.now());
        detailResponse.setUpdateTime(LocalDateTime.now());

        doAnswer(invocation -> {
            sleepQuietly(2L);
            return document;
        }).when(documentMapper).selectById(DOCUMENT_ID);
        doAnswer(invocation -> {
            sleepQuietly(2L);
            return detailResponse;
        }).when(documentMapper).selectUserDocumentDetail(DOCUMENT_ID, USER_ID);

        DocumentServiceImpl service = new DocumentServiceImpl(
                documentMapper,
                fileRecordMapper,
                redisHarness.redisTemplate(),
                new ObjectMapper().registerModule(new JavaTimeModule())
        );

        String cacheKey = CommonConstants.buildDocumentDetailCacheKey(USER_ID, DOCUMENT_ID);
        int samples = 120;
        List<Long> missDurations = new ArrayList<>(samples);
        for (int i = 0; i < samples; i++) {
            redisHarness.store().remove(cacheKey);
            missDurations.add(measureNanos(() -> service.getDetailById(DOCUMENT_ID, USER_ID)));
        }

        redisHarness.store().remove(cacheKey);
        service.getDetailById(DOCUMENT_ID, USER_ID);
        List<Long> hitDurations = new ArrayList<>(samples);
        for (int i = 0; i < samples; i++) {
            hitDurations.add(measureNanos(() -> service.getDetailById(DOCUMENT_ID, USER_ID)));
        }

        return new CacheScenarioResult(
                "document_detail_cache",
                samples,
                1,
                toStats(missDurations),
                toStats(hitDurations)
        );
    }

    private CacheScenarioResult benchmarkQaAnswerCache() {
        RedisHarness redisHarness = new RedisHarness();
        DocumentMapper documentMapper = mock(DocumentMapper.class);
        AiAnswerService aiAnswerService = mock(AiAnswerService.class);
        DocumentQaHistoryMapper historyMapper = mock(DocumentQaHistoryMapper.class);

        Document document = new Document();
        document.setId(DOCUMENT_ID);
        document.setUserId(USER_ID);
        document.setContent("This benchmark document explains cache miss and cache hit behavior.");
        LocalDateTime version = LocalDateTime.of(2026, 4, 7, 23, 30, 0);
        document.setUpdateTime(version);
        when(documentMapper.selectById(DOCUMENT_ID)).thenReturn(document);
        when(historyMapper.insert(ArgumentMatchers.any(DocumentQaHistory.class))).thenReturn(1);

        AtomicInteger aiCallCounter = new AtomicInteger(0);
        doAnswer(invocation -> {
            aiCallCounter.incrementAndGet();
            sleepQuietly(8L);
            return "cached benchmark answer";
        }).when(aiAnswerService).answer(ArgumentMatchers.anyString(), ArgumentMatchers.eq("cache benchmark question"));

        AiRetryExecutor retryExecutor = new AiRetryExecutor();
        ReflectionTestUtils.setField(retryExecutor, "retryEnabled", true);
        ReflectionTestUtils.setField(retryExecutor, "maxAttempts", 2);
        ReflectionTestUtils.setField(retryExecutor, "initialBackoffMs", 1L);
        ReflectionTestUtils.setField(retryExecutor, "backoffMultiplier", 2.0D);
        ReflectionTestUtils.setField(retryExecutor, "maxBackoffMs", 2L);

        DocumentQaServiceImpl service = new DocumentQaServiceImpl(
                documentMapper,
                aiAnswerService,
                historyMapper,
                redisHarness.redisTemplate(),
                new RedisTokenBucketRateLimiter(redisHarness.redisTemplate()),
                retryExecutor
        );

        String question = "cache benchmark question";
        String cacheKey = CommonConstants.buildQaAnswerCacheKey(
                USER_ID,
                DOCUMENT_ID,
                version.toString(),
                sha256Hex(CommonConstants.QA_DEFAULT_SESSION_ID + "|" + question)
        );

        int samples = 90;
        List<Long> missDurations = new ArrayList<>(samples);
        for (int i = 0; i < samples; i++) {
            redisHarness.store().remove(cacheKey);
            missDurations.add(measureNanos(() -> service.answer(USER_ID, DOCUMENT_ID, question)));
        }

        redisHarness.store().remove(cacheKey);
        service.answer(USER_ID, DOCUMENT_ID, question);
        List<Long> hitDurations = new ArrayList<>(samples);
        for (int i = 0; i < samples; i++) {
            hitDurations.add(measureNanos(() -> service.answer(USER_ID, DOCUMENT_ID, question)));
        }

        return new CacheScenarioResult(
                "qa_answer_cache",
                samples,
                1,
                toStats(missDurations),
                toStats(hitDurations)
        );
    }

    private AiModeScenarioResult benchmarkAiModeComparison() throws Exception {
        String context = "Benchmark context for AI mode comparison";
        String question = "Explain benchmark result";
        int requests = 120;
        int concurrency = 5;

        MockAiAnswerService mockService = new MockAiAnswerService();
        ScenarioStats mockStats = runConcurrentScenario(requests, concurrency, () -> mockService.answer(context, question));

        HttpServer server = startLocalProvider();
        try {
            RealAiAnswerService realService = new RealAiAnswerService();
            ReflectionTestUtils.setField(realService, "baseUrl", "http://127.0.0.1:" + server.getAddress().getPort());
            ReflectionTestUtils.setField(realService, "apiKey", "benchmark-key");
            ReflectionTestUtils.setField(realService, "model", "benchmark-model");
            ReflectionTestUtils.setField(realService, "connectTimeoutMs", 1500);
            ReflectionTestUtils.setField(realService, "readTimeoutMs", 3000);
            ReflectionTestUtils.setField(realService, "temperature", 0.2D);
            ReflectionTestUtils.setField(realService, "maxOutputTokens", 128);
            ReflectionTestUtils.setField(realService, "inputCostPer1kUsd", 0.01D);
            ReflectionTestUtils.setField(realService, "outputCostPer1kUsd", 0.03D);
            ReflectionTestUtils.invokeMethod(realService, "validateOnStartup");

            ScenarioStats realStats = runConcurrentScenario(requests, concurrency, () -> realService.answer(context, question));
            return new AiModeScenarioResult(requests, concurrency, mockStats, realStats);
        } finally {
            server.stop(0);
        }
    }

    private CreateMainChainScenarioResult benchmarkCreateMainChain() throws Exception {
        CreateApiScenarioResult documentCreate = benchmarkDocumentCreateApi();
        CreateApiScenarioResult parseCreate = benchmarkParseTaskCreateApi();
        AsyncNonBlockingProofResult asyncProof = benchmarkParseTaskCreateAsyncNonBlocking();
        return new CreateMainChainScenarioResult(documentCreate, parseCreate, asyncProof);
    }

    private CreateApiScenarioResult benchmarkDocumentCreateApi() throws Exception {
        DocumentMapper documentMapper = mock(DocumentMapper.class);
        FileRecordMapper fileRecordMapper = mock(FileRecordMapper.class);
        DocumentServiceImpl service = new DocumentServiceImpl(
                documentMapper,
                fileRecordMapper,
                RedisHarness.buildRedisTemplate(new ConcurrentHashMap<>()),
                new ObjectMapper().registerModule(new JavaTimeModule())
        );

        AtomicLong documentIdGenerator = new AtomicLong(30_000L);
        when(fileRecordMapper.selectById(ArgumentMatchers.anyLong())).thenAnswer(invocation -> {
            Long fileRecordId = invocation.getArgument(0);
            FileRecord fileRecord = new FileRecord();
            fileRecord.setId(fileRecordId);
            fileRecord.setUserId(USER_ID);
            fileRecord.setFileName("bench-" + fileRecordId + ".md");
            fileRecord.setFileExt("md");
            fileRecord.setFileSize(2048L);
            return fileRecord;
        });
        when(documentMapper.selectLatestByUserAndFileRecordId(ArgumentMatchers.eq(USER_ID), ArgumentMatchers.anyLong()))
                .thenReturn(null);
        doAnswer(invocation -> {
            sleepQuietly(1L);
            Document document = invocation.getArgument(0);
            document.setId(documentIdGenerator.incrementAndGet());
            return 1;
        }).when(documentMapper).insert(ArgumentMatchers.any(Document.class));

        AtomicLong warmupCounter = new AtomicLong(0L);
        warmup(CREATE_WARMUP, () -> {
            long fileRecordId = 40_000L + warmupCounter.incrementAndGet();
            DocumentCreateResponse response = service.create(fileRecordId, USER_ID);
            if (response == null || response.getId() == null) {
                throw new IllegalStateException("warmup document/create failed");
            }
            return "warmup-ok";
        });

        AtomicLong requestCounter = new AtomicLong(0L);
        ScenarioStats stats = runConcurrentScenario(CREATE_REQUESTS, CREATE_CONCURRENCY, () -> {
            long seq = requestCounter.incrementAndGet();
            long fileRecordId = 50_000L + seq;
            DocumentCreateResponse response = service.create(fileRecordId, USER_ID);
            if (response == null || response.getId() == null) {
                throw new IllegalStateException("document/create returned empty response");
            }
            if (!ParseStatusConstants.PENDING.equals(response.getParseStatus())) {
                throw new IllegalStateException("document/create parseStatus should be PENDING");
            }
            if (!Boolean.FALSE.equals(response.getReused())) {
                throw new IllegalStateException("document/create should be non-reused in benchmark");
            }
            return "ok";
        });
        return new CreateApiScenarioResult("POST /api/document/create", CREATE_REQUESTS, CREATE_CONCURRENCY, stats);
    }

    private CreateApiScenarioResult benchmarkParseTaskCreateApi() throws Exception {
        ParseTaskMapper parseTaskMapper = mock(ParseTaskMapper.class);
        DocumentMapper documentMapper = mock(DocumentMapper.class);
        ParseTaskOutboxRelayService outboxRelayService = mock(ParseTaskOutboxRelayService.class);
        RedissonClient redissonClient = mock(RedissonClient.class);
        StringRedisTemplate stringRedisTemplate = mock(StringRedisTemplate.class);
        RLock lock = mock(RLock.class);

        when(redissonClient.getLock(ArgumentMatchers.anyString())).thenReturn(lock);
        try {
            when(lock.tryLock(ArgumentMatchers.anyLong(), ArgumentMatchers.any(TimeUnit.class))).thenReturn(true);
        } catch (InterruptedException ex) {
            throw new IllegalStateException(ex);
        }
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        when(documentMapper.selectById(ArgumentMatchers.anyLong())).thenAnswer(invocation -> {
            Long documentId = invocation.getArgument(0);
            Document document = new Document();
            document.setId(documentId);
            document.setUserId(USER_ID);
            document.setFileRecordId(60_000L + documentId);
            return document;
        });
        when(parseTaskMapper.selectLatestByUserAndDocumentId(ArgumentMatchers.eq(USER_ID), ArgumentMatchers.anyLong()))
                .thenReturn(null);

        AtomicLong taskIdGenerator = new AtomicLong(70_000L);
        doAnswer(invocation -> {
            sleepQuietly(1L);
            ParseTask parseTask = invocation.getArgument(0);
            parseTask.setId(taskIdGenerator.incrementAndGet());
            return 1;
        }).when(parseTaskMapper).insert(ArgumentMatchers.any(ParseTask.class));

        AtomicLong outboxIdGenerator = new AtomicLong(80_000L);
        AtomicInteger outboxAppendCounter = new AtomicInteger(0);
        AtomicInteger outboxDispatchCounter = new AtomicInteger(0);
        when(outboxRelayService.appendPending(ArgumentMatchers.anyLong(), ArgumentMatchers.anyLong(),
                ArgumentMatchers.anyLong(), ArgumentMatchers.anyString())).thenAnswer(invocation -> {
            outboxAppendCounter.incrementAndGet();
            return outboxIdGenerator.incrementAndGet();
        });
        when(outboxRelayService.dispatchByOutboxId(ArgumentMatchers.anyLong(), ArgumentMatchers.anyString()))
                .thenAnswer(invocation -> {
                    outboxDispatchCounter.incrementAndGet();
                    return true;
                });

        ParseTaskServiceImpl service = new ParseTaskServiceImpl(
                parseTaskMapper,
                documentMapper,
                outboxRelayService,
                redissonClient,
                stringRedisTemplate
        );
        ReflectionTestUtils.setField(service, "parseTaskLockFailMessage", "当前文档解析任务处理中，请稍后重试");

        AtomicLong warmupCounter = new AtomicLong(0L);
        warmup(CREATE_WARMUP, () -> {
            long documentId = 90_000L + warmupCounter.incrementAndGet();
            ParseTaskCreateResponse response = service.create(documentId, USER_ID);
            if (response == null || response.getTaskId() == null) {
                throw new IllegalStateException("warmup task/parse/create failed");
            }
            return "warmup-ok";
        });
        outboxAppendCounter.set(0);
        outboxDispatchCounter.set(0);

        AtomicLong requestCounter = new AtomicLong(0L);
        ScenarioStats stats = runConcurrentScenario(CREATE_REQUESTS, CREATE_CONCURRENCY, () -> {
            long seq = requestCounter.incrementAndGet();
            long documentId = 100_000L + seq;
            ParseTaskCreateResponse response = service.create(documentId, USER_ID);
            if (response == null || response.getTaskId() == null) {
                throw new IllegalStateException("task/parse/create returned empty response");
            }
            if (!ParseStatusConstants.PENDING.equals(response.getStatus())) {
                throw new IllegalStateException("task/parse/create status should be PENDING");
            }
            if (!Boolean.FALSE.equals(response.getReused())) {
                throw new IllegalStateException("task/parse/create should be non-reused in benchmark");
            }
            return "ok";
        });

        double outboxAppendSuccessRate = CREATE_REQUESTS == 0
                ? 0.0D : outboxAppendCounter.get() * 100.0D / CREATE_REQUESTS;
        double outboxDispatchSuccessRate = CREATE_REQUESTS == 0
                ? 0.0D : outboxDispatchCounter.get() * 100.0D / CREATE_REQUESTS;
        return new CreateApiScenarioResult(
                "POST /api/task/parse/create",
                CREATE_REQUESTS,
                CREATE_CONCURRENCY,
                stats,
                outboxAppendSuccessRate,
                outboxDispatchSuccessRate
        );
    }

    private AsyncNonBlockingProofResult benchmarkParseTaskCreateAsyncNonBlocking() throws Exception {
        ParseTaskMapper parseTaskMapper = mock(ParseTaskMapper.class);
        DocumentMapper documentMapper = mock(DocumentMapper.class);
        ParseTaskOutboxRelayService outboxRelayService = mock(ParseTaskOutboxRelayService.class);
        RedissonClient redissonClient = mock(RedissonClient.class);
        StringRedisTemplate stringRedisTemplate = mock(StringRedisTemplate.class);
        RLock lock = mock(RLock.class);

        when(redissonClient.getLock(ArgumentMatchers.anyString())).thenReturn(lock);
        try {
            when(lock.tryLock(ArgumentMatchers.anyLong(), ArgumentMatchers.any(TimeUnit.class))).thenReturn(true);
        } catch (InterruptedException ex) {
            throw new IllegalStateException(ex);
        }
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        when(documentMapper.selectById(ArgumentMatchers.anyLong())).thenAnswer(invocation -> {
            Long documentId = invocation.getArgument(0);
            Document document = new Document();
            document.setId(documentId);
            document.setUserId(USER_ID);
            document.setFileRecordId(120_000L + documentId);
            return document;
        });
        when(parseTaskMapper.selectLatestByUserAndDocumentId(ArgumentMatchers.eq(USER_ID), ArgumentMatchers.anyLong()))
                .thenReturn(null);

        AtomicLong taskIdGenerator = new AtomicLong(130_000L);
        doAnswer(invocation -> {
            sleepQuietly(1L);
            ParseTask parseTask = invocation.getArgument(0);
            parseTask.setId(taskIdGenerator.incrementAndGet());
            return 1;
        }).when(parseTaskMapper).insert(ArgumentMatchers.any(ParseTask.class));

        AtomicLong outboxIdGenerator = new AtomicLong(140_000L);
        AtomicInteger outboxAppendCounter = new AtomicInteger(0);
        AtomicInteger outboxDispatchCounter = new AtomicInteger(0);
        Map<Long, Long> outboxByTaskId = new ConcurrentHashMap<>();
        Map<Long, AsyncLifecycle> lifecycleByOutboxId = new ConcurrentHashMap<>();
        CountDownLatch parseFinishLatch = new CountDownLatch(ASYNC_PROOF_REQUESTS);
        ExecutorService asyncParsePool = Executors.newFixedThreadPool(12);

        when(outboxRelayService.appendPending(ArgumentMatchers.anyLong(), ArgumentMatchers.anyLong(),
                ArgumentMatchers.anyLong(), ArgumentMatchers.anyString())).thenAnswer(invocation -> {
            outboxAppendCounter.incrementAndGet();
            Long taskId = invocation.getArgument(0);
            Long outboxId = outboxIdGenerator.incrementAndGet();
            outboxByTaskId.put(taskId, outboxId);
            lifecycleByOutboxId.putIfAbsent(outboxId, new AsyncLifecycle());
            return outboxId;
        });
        when(outboxRelayService.dispatchByOutboxId(ArgumentMatchers.anyLong(), ArgumentMatchers.anyString()))
                .thenAnswer(invocation -> {
                    outboxDispatchCounter.incrementAndGet();
                    Long outboxId = invocation.getArgument(0);
                    asyncParsePool.submit(() -> {
                        sleepQuietly(ASYNC_CONSUMER_DELAY_MS);
                        lifecycleByOutboxId.computeIfAbsent(outboxId, key -> new AsyncLifecycle())
                                .markParseFinished(System.nanoTime());
                        parseFinishLatch.countDown();
                    });
                    return true;
                });

        ParseTaskServiceImpl service = new ParseTaskServiceImpl(
                parseTaskMapper,
                documentMapper,
                outboxRelayService,
                redissonClient,
                stringRedisTemplate
        );
        ReflectionTestUtils.setField(service, "parseTaskLockFailMessage", "当前文档解析任务处理中，请稍后重试");

        AtomicLong requestCounter = new AtomicLong(0L);
        ScenarioStats responseLatency = runConcurrentScenario(ASYNC_PROOF_REQUESTS, ASYNC_PROOF_CONCURRENCY, () -> {
            long seq = requestCounter.incrementAndGet();
            long documentId = 150_000L + seq;
            ParseTaskCreateResponse response = service.create(documentId, USER_ID);
            if (response == null || response.getTaskId() == null) {
                throw new IllegalStateException("async proof parse/create returned empty response");
            }
            if (!ParseStatusConstants.PENDING.equals(response.getStatus())) {
                throw new IllegalStateException("async proof parse/create status should be PENDING");
            }
            Long outboxId = outboxByTaskId.get(response.getTaskId());
            if (outboxId == null) {
                throw new IllegalStateException("async proof outbox id missing");
            }
            lifecycleByOutboxId.computeIfAbsent(outboxId, key -> new AsyncLifecycle())
                    .markResponse(System.nanoTime());
            return "ok";
        });

        boolean parseCompleted = parseFinishLatch.await(30, TimeUnit.SECONDS);
        asyncParsePool.shutdown();
        asyncParsePool.awaitTermination(5, TimeUnit.SECONDS);

        int comparable = 0;
        int responseFirst = 0;
        for (AsyncLifecycle lifecycle : lifecycleByOutboxId.values()) {
            if (lifecycle.responseAtNanos() > 0L && lifecycle.parseFinishedAtNanos() > 0L) {
                comparable++;
                if (lifecycle.responseAtNanos() < lifecycle.parseFinishedAtNanos()) {
                    responseFirst++;
                }
            }
        }
        double responseBeforeParseFinishRate = comparable == 0 ? 0.0D : responseFirst * 100.0D / comparable;
        double outboxAppendSuccessRate = ASYNC_PROOF_REQUESTS == 0
                ? 0.0D : outboxAppendCounter.get() * 100.0D / ASYNC_PROOF_REQUESTS;
        double outboxDispatchSuccessRate = ASYNC_PROOF_REQUESTS == 0
                ? 0.0D : outboxDispatchCounter.get() * 100.0D / ASYNC_PROOF_REQUESTS;
        return new AsyncNonBlockingProofResult(
                "POST /api/task/parse/create + slow async consumer",
                ASYNC_PROOF_REQUESTS,
                ASYNC_PROOF_CONCURRENCY,
                responseLatency,
                ASYNC_CONSUMER_DELAY_MS,
                parseCompleted,
                responseBeforeParseFinishRate,
                outboxAppendSuccessRate,
                outboxDispatchSuccessRate
        );
    }

    private ScenarioStats runConcurrentScenario(int requests, int concurrency, Callable<String> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(concurrency);
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<RequestResult>> futures = new ArrayList<>(requests);
        for (int i = 0; i < requests; i++) {
            futures.add(pool.submit(() -> {
                startGate.await(2, TimeUnit.SECONDS);
                long start = System.nanoTime();
                try {
                    task.call();
                    return new RequestResult(true, System.nanoTime() - start);
                } catch (Exception ex) {
                    return new RequestResult(false, System.nanoTime() - start);
                }
            }));
        }

        startGate.countDown();
        List<Long> durations = new ArrayList<>(requests);
        int success = 0;
        for (Future<RequestResult> future : futures) {
            RequestResult result = future.get(8, TimeUnit.SECONDS);
            if (result.success()) {
                success++;
            }
            durations.add(result.durationNanos());
        }

        pool.shutdown();
        pool.awaitTermination(2, TimeUnit.SECONDS);

        ScenarioStats latency = toStats(durations);
        double successRate = requests == 0 ? 0.0D : (success * 100.0D / requests);
        return new ScenarioStats(latency.avgMs(), latency.p95Ms(), successRate);
    }

    private HttpServer startLocalProvider() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.createContext("/chat/completions", exchange -> {
            sleepQuietly(6L);
            byte[] body = """
                    {
                      "choices": [{"message": {"content": "local real answer"}}],
                      "usage": {"prompt_tokens": 80, "completion_tokens": 24, "total_tokens": 104}
                    }
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            }
        });
        server.start();
        return server;
    }

    private void warmup(int rounds, Callable<String> task) throws Exception {
        for (int i = 0; i < rounds; i++) {
            task.call();
        }
    }

    private void writeArtifacts(BenchmarkReport report) throws Exception {
        Path reportPath = Path.of("..", "docs", "ai-dev", "benchmarks", "STAGE11_TASK11_6_RESULTS.md").normalize();
        Path jsonPath = Path.of("..", "docs", "ai-dev", "benchmarks", "artifacts", "task11_6_latest.json").normalize();

        Files.createDirectories(reportPath.getParent());
        Files.createDirectories(jsonPath.getParent());

        String markdown = buildMarkdown(report);
        Files.writeString(reportPath, markdown, StandardCharsets.UTF_8);

        Map<String, Object> jsonPayload = new HashMap<>();
        jsonPayload.put("generatedAt", report.generatedAt());
        jsonPayload.put("documentDetailCache", report.documentDetailCache());
        jsonPayload.put("qaAnswerCache", report.qaAnswerCache());
        jsonPayload.put("aiMode", report.aiMode());
        jsonPayload.put("createMainChain", report.createMainChain());
        jsonPayload.put("environment", Map.of(
                "jdk", System.getProperty("java.version"),
                "os", System.getProperty("os.name") + " " + System.getProperty("os.version")
        ));
        String json = new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(jsonPayload);
        Files.writeString(jsonPath, json, StandardCharsets.UTF_8);
    }

    private String buildMarkdown(BenchmarkReport report) {
        StringBuilder builder = new StringBuilder();
        builder.append("# Stage 11.6 Bench Results\n\n");
        builder.append("- GeneratedAt: `").append(report.generatedAt()).append("`\n");
        builder.append("- Tool: `JUnit benchmark harness + local HTTP provider + in-memory Redis mock`\n");
        builder.append("- Scope: `document detail cache`, `qa answer cache`, `ai mock vs real(local)`, `create main chain latency`\n\n");

        appendCacheSection(builder, "Document Detail Cache", report.documentDetailCache());
        appendCacheSection(builder, "QA Answer Cache", report.qaAnswerCache());
        appendCreateChainSection(builder, report.createMainChain());

        builder.append("## AI Mode Comparison\n\n");
        builder.append("| mode | requests | concurrency | avg(ms) | p95(ms) | successRate(%) |\n");
        builder.append("|---|---:|---:|---:|---:|---:|\n");
        builder.append("| mock | ").append(report.aiMode().requests()).append(" | ")
                .append(report.aiMode().concurrency()).append(" | ")
                .append(formatMs(report.aiMode().mockStats().avgMs())).append(" | ")
                .append(formatMs(report.aiMode().mockStats().p95Ms())).append(" | ")
                .append(formatRate(report.aiMode().mockStats().successRate())).append(" |\n");
        builder.append("| real(local) | ").append(report.aiMode().requests()).append(" | ")
                .append(report.aiMode().concurrency()).append(" | ")
                .append(formatMs(report.aiMode().realStats().avgMs())).append(" | ")
                .append(formatMs(report.aiMode().realStats().p95Ms())).append(" | ")
                .append(formatRate(report.aiMode().realStats().successRate())).append(" |\n\n");

        boolean canClaimMillisecondResponse = canClaimMillisecondResponse(report.createMainChain());
        builder.append("## Resume Claim Boundary (Create Main Chain)\n\n");
        if (canClaimMillisecondResponse) {
            builder.append("- Conclusion: `POST /api/document/create` and `POST /api/task/parse/create` show stable millisecond-level API return latency in this benchmark scope.\n");
        } else {
            builder.append("- Conclusion: use conservative wording: `asynchronous create chain returns quickly (sub-100ms to low-hundreds-ms range depending on environment)`.\n");
        }
        builder.append("- Scope boundary: this benchmark measures **API return latency** only; it does not represent end-to-end parse completion latency.\n");
        builder.append("- Async evidence: `task/parse/create` response is expected to return while task remains `PENDING`; slow async parse completion should happen later.\n\n");

        builder.append("## Notes\n\n");
        builder.append("- cache scenarios use same service path and key strategy as production code; miss/hit are controlled by cache key cleanup and warmup.\n");
        builder.append("- real(local) uses `RealAiAnswerService` with local HTTP provider to measure HTTP overhead without external network jitter.\n");
        builder.append("- create chain benchmark reuses service-layer main path (`DocumentServiceImpl.create` / `ParseTaskServiceImpl.create`) and reports avg/p95/successRate.\n");
        builder.append("- async non-blocking scenario uses a simulated slow consumer to validate response-before-parse-finish behavior; this is evidence of non-blocking orchestration, not parse throughput.\n");
        builder.append("- metrics are not reset in this test; use generated raw JSON for reproducible comparison and rerun when environment changes.\n");
        return builder.toString();
    }

    private void appendCreateChainSection(StringBuilder builder, CreateMainChainScenarioResult result) {
        builder.append("## Create Main Chain Latency\n\n");
        builder.append("| scenario | requests | concurrency | avg(ms) | p95(ms) | successRate(%) | outboxAppendRate(%) | outboxDispatchRate(%) |\n");
        builder.append("|---|---:|---:|---:|---:|---:|---:|---:|\n");
        builder.append("| ").append(result.documentCreate().scenario()).append(" | ")
                .append(result.documentCreate().requests()).append(" | ")
                .append(result.documentCreate().concurrency()).append(" | ")
                .append(formatMs(result.documentCreate().stats().avgMs())).append(" | ")
                .append(formatMs(result.documentCreate().stats().p95Ms())).append(" | ")
                .append(formatRate(result.documentCreate().stats().successRate())).append(" | ")
                .append(formatRate(result.documentCreate().outboxAppendRate())).append(" | ")
                .append(formatRate(result.documentCreate().outboxDispatchRate())).append(" |\n");
        builder.append("| ").append(result.parseCreate().scenario()).append(" | ")
                .append(result.parseCreate().requests()).append(" | ")
                .append(result.parseCreate().concurrency()).append(" | ")
                .append(formatMs(result.parseCreate().stats().avgMs())).append(" | ")
                .append(formatMs(result.parseCreate().stats().p95Ms())).append(" | ")
                .append(formatRate(result.parseCreate().stats().successRate())).append(" | ")
                .append(formatRate(result.parseCreate().outboxAppendRate())).append(" | ")
                .append(formatRate(result.parseCreate().outboxDispatchRate())).append(" |\n\n");

        builder.append("## Async Non-Blocking Proof (Create vs Parse Completion)\n\n");
        builder.append("| scenario | requests | concurrency | avg(ms) | p95(ms) | successRate(%) | responseBeforeParseFinishRate(%) | slowConsumerDelay(ms) | parseCompletionObserved |\n");
        builder.append("|---|---:|---:|---:|---:|---:|---:|---:|---|\n");
        builder.append("| ").append(result.asyncNonBlockingProof().scenario()).append(" | ")
                .append(result.asyncNonBlockingProof().requests()).append(" | ")
                .append(result.asyncNonBlockingProof().concurrency()).append(" | ")
                .append(formatMs(result.asyncNonBlockingProof().responseLatency().avgMs())).append(" | ")
                .append(formatMs(result.asyncNonBlockingProof().responseLatency().p95Ms())).append(" | ")
                .append(formatRate(result.asyncNonBlockingProof().responseLatency().successRate())).append(" | ")
                .append(formatRate(result.asyncNonBlockingProof().responseBeforeParseFinishRate())).append(" | ")
                .append(result.asyncNonBlockingProof().slowConsumerDelayMs()).append(" | ")
                .append(result.asyncNonBlockingProof().parseCompletionObserved() ? "yes" : "no")
                .append(" |\n\n");
    }

    private boolean canClaimMillisecondResponse(CreateMainChainScenarioResult result) {
        return result.documentCreate().stats().successRate() >= 99.0D
                && result.parseCreate().stats().successRate() >= 99.0D
                && result.documentCreate().stats().avgMs() <= 100.0D
                && result.parseCreate().stats().avgMs() <= 100.0D
                && result.documentCreate().stats().p95Ms() <= 200.0D
                && result.parseCreate().stats().p95Ms() <= 200.0D;
    }

    private void appendCacheSection(StringBuilder builder, String title, CacheScenarioResult result) {
        builder.append("## ").append(title).append("\n\n");
        builder.append("| phase | samples | concurrency | avg(ms) | p95(ms) |\n");
        builder.append("|---|---:|---:|---:|---:|\n");
        builder.append("| miss | ").append(result.samples()).append(" | ")
                .append(result.concurrency()).append(" | ")
                .append(formatMs(result.missStats().avgMs())).append(" | ")
                .append(formatMs(result.missStats().p95Ms())).append(" |\n");
        builder.append("| hit | ").append(result.samples()).append(" | ")
                .append(result.concurrency()).append(" | ")
                .append(formatMs(result.hitStats().avgMs())).append(" | ")
                .append(formatMs(result.hitStats().p95Ms())).append(" |\n\n");
    }

    private long measureNanos(Runnable runnable) {
        long start = System.nanoTime();
        runnable.run();
        return System.nanoTime() - start;
    }

    private ScenarioStats toStats(List<Long> nanos) {
        if (nanos.isEmpty()) {
            return new ScenarioStats(0.0D, 0.0D, 0.0D);
        }
        double avgMs = nanos.stream().mapToLong(Long::longValue).average().orElse(0.0D) / 1_000_000.0D;
        List<Long> sorted = nanos.stream().sorted(Comparator.naturalOrder()).toList();
        int p95Index = (int) Math.ceil(sorted.size() * 0.95D) - 1;
        p95Index = Math.max(0, Math.min(p95Index, sorted.size() - 1));
        double p95Ms = sorted.get(p95Index) / 1_000_000.0D;
        return new ScenarioStats(avgMs, p95Ms, 100.0D);
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("benchmark sleep interrupted", interruptedException);
        }
    }

    private String formatMs(double value) {
        return String.format(java.util.Locale.ROOT, "%.3f", value);
    }

    private String formatRate(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    private static final class RedisHarness {

        private final StringRedisTemplate redisTemplate;
        private final Map<String, String> store;

        private RedisHarness() {
            this.store = new ConcurrentHashMap<>();
            this.redisTemplate = buildRedisTemplate(store);
        }

        private StringRedisTemplate redisTemplate() {
            return redisTemplate;
        }

        private Map<String, String> store() {
            return store;
        }

        private static StringRedisTemplate buildRedisTemplate(Map<String, String> cacheStore) {
            StringRedisTemplate redis = mock(StringRedisTemplate.class);
            @SuppressWarnings("unchecked")
            ValueOperations<String, String> valueOperations = (ValueOperations<String, String>) mock(ValueOperations.class);
            @SuppressWarnings("unchecked")
            ListOperations<String, String> listOperations = (ListOperations<String, String>) mock(ListOperations.class);
            when(redis.opsForValue()).thenReturn(valueOperations);
            when(redis.opsForList()).thenReturn(listOperations);
            when(listOperations.range(ArgumentMatchers.anyString(), ArgumentMatchers.anyLong(), ArgumentMatchers.anyLong()))
                    .thenReturn(List.of());
            doAnswer(invocation -> 1L).when(listOperations).rightPush(ArgumentMatchers.anyString(), ArgumentMatchers.anyString());
            doAnswer(invocation -> null).when(listOperations).trim(
                    ArgumentMatchers.anyString(),
                    ArgumentMatchers.anyLong(),
                    ArgumentMatchers.anyLong()
            );

            doAnswer(invocation -> cacheStore.get(invocation.getArgument(0)))
                    .when(valueOperations).get(ArgumentMatchers.anyString());
            doAnswer(invocation -> {
                cacheStore.put(invocation.getArgument(0), invocation.getArgument(1));
                return null;
            }).when(valueOperations).set(
                    ArgumentMatchers.anyString(),
                    ArgumentMatchers.anyString(),
                    ArgumentMatchers.anyLong(),
                    ArgumentMatchers.any(TimeUnit.class)
            );
            doAnswer(invocation -> 1L).when(valueOperations).increment(ArgumentMatchers.anyString());
            doAnswer(invocation -> {
                cacheStore.remove(invocation.getArgument(0));
                return true;
            }).when(redis).delete(ArgumentMatchers.anyString());
            doAnswer(invocation -> true).when(redis)
                    .expire(ArgumentMatchers.anyString(), ArgumentMatchers.anyLong(), ArgumentMatchers.any(TimeUnit.class));

            return redis;
        }
    }

    private record CacheScenarioResult(String scenario,
                                       int samples,
                                       int concurrency,
                                       ScenarioStats missStats,
                                       ScenarioStats hitStats) {
    }

    private record AiModeScenarioResult(int requests,
                                        int concurrency,
                                        ScenarioStats mockStats,
                                        ScenarioStats realStats) {
    }

    private record CreateApiScenarioResult(String scenario,
                                           int requests,
                                           int concurrency,
                                           ScenarioStats stats,
                                           double outboxAppendRate,
                                           double outboxDispatchRate) {
        private CreateApiScenarioResult(String scenario,
                                        int requests,
                                        int concurrency,
                                        ScenarioStats stats) {
            this(scenario, requests, concurrency, stats, 0.0D, 0.0D);
        }
    }

    private record AsyncNonBlockingProofResult(String scenario,
                                               int requests,
                                               int concurrency,
                                               ScenarioStats responseLatency,
                                               int slowConsumerDelayMs,
                                               boolean parseCompletionObserved,
                                               double responseBeforeParseFinishRate,
                                               double outboxAppendRate,
                                               double outboxDispatchRate) {
    }

    private record CreateMainChainScenarioResult(CreateApiScenarioResult documentCreate,
                                                 CreateApiScenarioResult parseCreate,
                                                 AsyncNonBlockingProofResult asyncNonBlockingProof) {
    }

    private record ScenarioStats(double avgMs,
                                 double p95Ms,
                                 double successRate) {
    }

    private record RequestResult(boolean success,
                                 long durationNanos) {
    }

    private record BenchmarkReport(CacheScenarioResult documentDetailCache,
                                   CacheScenarioResult qaAnswerCache,
                                   AiModeScenarioResult aiMode,
                                   CreateMainChainScenarioResult createMainChain) {
        String generatedAt() {
            return OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        }
    }

    private static final class AsyncLifecycle {
        private volatile long responseAtNanos;
        private volatile long parseFinishedAtNanos;

        private void markResponse(long nanos) {
            if (responseAtNanos == 0L) {
                responseAtNanos = nanos;
            }
        }

        private void markParseFinished(long nanos) {
            parseFinishedAtNanos = nanos;
        }

        private long responseAtNanos() {
            return responseAtNanos;
        }

        private long parseFinishedAtNanos() {
            return parseFinishedAtNanos;
        }
    }
}



