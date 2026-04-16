package com.docpilot.backend.ai.service.impl;

import com.docpilot.backend.ai.entity.DocumentQaHistory;
import com.docpilot.backend.ai.mapper.DocumentQaHistoryMapper;
import com.docpilot.backend.ai.service.AiAnswerService;
import com.docpilot.backend.ai.service.DocumentQaService;
import com.docpilot.backend.ai.vo.DocumentQaHistoryItemResponse;
import com.docpilot.backend.ai.vo.DocumentQaResponse;
import com.docpilot.backend.common.constant.CommonConstants;
import com.docpilot.backend.common.error.ErrorCode;
import com.docpilot.backend.common.exception.BusinessException;
import com.docpilot.backend.common.limiter.RedisTokenBucketRateLimiter;
import com.docpilot.backend.common.metrics.DocPilotMetrics;
import com.docpilot.backend.common.util.ValidationUtils;
import com.docpilot.backend.document.entity.Document;
import com.docpilot.backend.document.mapper.DocumentMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

@Service
public class DocumentQaServiceImpl implements DocumentQaService {

    private static final Logger log = LoggerFactory.getLogger(DocumentQaServiceImpl.class);

    private static final int DEFAULT_MAX_DOCUMENT_CONTEXT_LENGTH = 4_000;
    private static final int DEFAULT_CHUNK_SIZE = 600;
    private static final int DEFAULT_CHUNK_OVERLAP = 120;
    private static final int MAX_CITATION_COUNT = 3;
    private static final int MAX_CITATION_SNIPPET_LENGTH = 180;
    private static final int DEFAULT_HISTORY_LIMIT = 10;
    private static final int MAX_HISTORY_LIMIT = 50;
    private static final long SSE_TIMEOUT_MS = 60_000L;
    private static final Pattern QUERY_SPLIT_PATTERN = Pattern.compile("[^\\p{L}\\p{N}]+", Pattern.UNICODE_CHARACTER_CLASS);

    private final DocumentMapper documentMapper;
    private final AiAnswerService aiAnswerService;
    private final DocumentQaHistoryMapper documentQaHistoryMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final RedisTokenBucketRateLimiter redisTokenBucketRateLimiter;
    private final AiRetryExecutor aiRetryExecutor;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.ai.max-document-context-length:4000}")
    private int maxDocumentContextLength = DEFAULT_MAX_DOCUMENT_CONTEXT_LENGTH;

    public DocumentQaServiceImpl(DocumentMapper documentMapper,
                                 AiAnswerService aiAnswerService,
                                 DocumentQaHistoryMapper documentQaHistoryMapper,
                                 StringRedisTemplate stringRedisTemplate,
                                 RedisTokenBucketRateLimiter redisTokenBucketRateLimiter,
                                 AiRetryExecutor aiRetryExecutor) {
        this.documentMapper = documentMapper;
        this.aiAnswerService = aiAnswerService;
        this.documentQaHistoryMapper = documentQaHistoryMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.redisTokenBucketRateLimiter = redisTokenBucketRateLimiter;
        this.aiRetryExecutor = aiRetryExecutor;
    }

    @Override
    public DocumentQaResponse answer(Long userId, Long documentId, String question, String sessionId) {
        checkQaRateLimit(userId);

        QaContext context = prepareQaContext(userId, documentId, question, sessionId);
        String promptQuestion = buildQuestionWithSessionContext(userId, context.documentId(), context.sessionId(), context.question());

        String answer = getOrGenerateAnswer(userId, context, promptQuestion);
        saveQaHistory(userId, context.documentId(), context.question(), answer);
        appendSessionContext(userId, context.documentId(), context.sessionId(), context.question(), answer);

        DocumentQaResponse response = new DocumentQaResponse();
        response.setDocumentId(context.documentId());
        response.setQuestion(context.question());
        response.setAnswer(answer);
        response.setSessionId(context.sessionId());
        response.setCitations(context.citations());
        return response;
    }

    @Override
    public SseEmitter streamAnswer(Long userId, Long documentId, String question, String sessionId) {
        checkQaRateLimit(userId);

        QaContext context = prepareQaContext(userId, documentId, question, sessionId);
        String promptQuestion = buildQuestionWithSessionContext(userId, context.documentId(), context.sessionId(), context.question());
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        CompletableFuture.runAsync(() -> doStreamAnswer(emitter, userId, context, promptQuestion));
        return emitter;
    }

    @Override
    public List<DocumentQaHistoryItemResponse> listHistory(Long userId, Long documentId, Integer limit) {
        ValidationUtils.requireNonNull(userId, "userId");
        ValidationUtils.requireNonNull(documentId, "documentId");
        ensureOwnedDocument(userId, documentId);
        return documentQaHistoryMapper.selectRecentByUserAndDocument(userId, documentId, resolveHistoryLimit(limit));
    }

    private void doStreamAnswer(SseEmitter emitter, Long userId, QaContext context, String promptQuestion) {
        AtomicBoolean anyChunkSent = new AtomicBoolean(false);
        StringBuilder answerBuilder = new StringBuilder();

        try {
            aiRetryExecutor.execute("qa.stream", () -> {
                try {
                    aiAnswerService.streamAnswer(context.documentContext(), promptQuestion, chunk -> {
                        if (chunk == null || chunk.isEmpty()) {
                            return;
                        }
                        anyChunkSent.set(true);
                        answerBuilder.append(chunk);
                        try {
                            sendEvent(emitter, "chunk", chunk);
                        } catch (Exception sendException) {
                            throw new RuntimeException(sendException);
                        }
                    });
                    if (answerBuilder.isEmpty()) {
                        throw new IllegalArgumentException("AI 流式回答为空");
                    }
                    return Boolean.TRUE;
                } catch (RuntimeException ex) {
                    if (anyChunkSent.get()) {
                        throw new IllegalStateException("stream interrupted after partial output", ex);
                    }
                    throw ex;
                }
            });

            String finalAnswer = answerBuilder.toString().trim();
            saveQaHistory(userId, context.documentId(), context.question(), finalAnswer);
            appendSessionContext(userId, context.documentId(), context.sessionId(), context.question(), finalAnswer);
            sendEvent(emitter, "done", "[DONE]");
            emitter.complete();
        } catch (Exception ex) {
            try {
                sendEvent(emitter, "error", "流式问答失败");
            } catch (Exception ignored) {
                // ignore
            }
            emitter.complete();
        }
    }

    private void sendEvent(SseEmitter emitter, String eventName, String data) throws Exception {
        emitter.send(SseEmitter.event().name(eventName).data(data));
    }

    private QaContext prepareQaContext(Long userId, Long documentId, String question, String sessionId) {
        ValidationUtils.requireNonNull(userId, "userId");
        ValidationUtils.requireNonNull(documentId, "documentId");
        ValidationUtils.requireNonBlank(question, "question");

        Document document = ensureOwnedDocument(userId, documentId);
        String rawContent = document.getContent();
        if (rawContent == null || rawContent.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.DOCUMENT_CONTENT_EMPTY, "document content is empty or not parsed yet");
        }

        String normalizedQuestion = question.trim();
        String normalizedContent = normalizeWhitespace(rawContent);

        List<DocumentChunk> chunks = splitIntoChunks(normalizedContent);
        List<String> terms = extractQueryTerms(normalizedQuestion);
        List<RankedChunk> rankedChunks = rankChunks(chunks, terms);

        String documentContext = buildDocumentContext(rankedChunks, normalizedContent);
        String documentVersion = resolveDocumentVersion(document.getUpdateTime());
        List<DocumentQaResponse.CitationItem> citations = buildCitations(rankedChunks);
        String normalizedSessionId = normalizeSessionId(sessionId);

        return new QaContext(documentId, normalizedQuestion, documentContext, documentVersion, citations, normalizedSessionId);
    }

    private Document ensureOwnedDocument(Long userId, Long documentId) {
        Document document = documentMapper.selectById(documentId);
        if (document == null) {
            throw new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND, "document does not exist");
        }
        if (!userId.equals(document.getUserId())) {
            throw new BusinessException(ErrorCode.DOCUMENT_FORBIDDEN, "document does not belong to current user");
        }
        return document;
    }

    private String getOrGenerateAnswer(Long userId, QaContext context, String promptQuestion) {
        String cacheKey = CommonConstants.buildQaAnswerCacheKey(
                userId,
                context.documentId(),
                context.documentVersion(),
                hashQuestion(context.sessionId() + "|" + context.question())
        );

        String cachedAnswer = getCachedAnswer(cacheKey);
        if (cachedAnswer != null) {
            DocPilotMetrics.recordCacheAccess("qa_answer", "hit");
            return cachedAnswer;
        }

        DocPilotMetrics.recordCacheAccess("qa_answer", "miss");
        String generated = generateAnswer(context.documentContext(), promptQuestion);
        cacheAnswer(cacheKey, generated);
        return generated;
    }

    private String generateAnswer(String documentContext, String promptQuestion) {
        try {
            return aiRetryExecutor.execute("qa.answer", () -> {
                String aiAnswer = aiAnswerService.answer(documentContext, promptQuestion);
                if (aiAnswer == null || aiAnswer.trim().isEmpty()) {
                    throw new IllegalArgumentException("AI answer is empty");
                }
                return aiAnswer.trim();
            });
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.AI_CALL_FAILED, "ai answer generation failed");
        }
    }

    private void saveQaHistory(Long userId, Long documentId, String question, String answer) {
        DocumentQaHistory history = new DocumentQaHistory();
        history.setUserId(userId);
        history.setDocumentId(documentId);
        history.setQuestion(question);
        history.setAnswer(answer);

        int inserted = documentQaHistoryMapper.insert(history);
        if (inserted <= 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "failed to save qa history");
        }
    }

    private String buildQuestionWithSessionContext(Long userId, Long documentId, String sessionId, String question) {
        List<SessionTurn> turns = loadSessionTurns(userId, documentId, sessionId);
        if (turns.isEmpty()) {
            return question;
        }

        StringBuilder prompt = new StringBuilder();
        prompt.append("历史问答：");
        int index = 1;
        for (SessionTurn turn : turns) {
            prompt.append("\nQ").append(index).append("：").append(turn.question());
            prompt.append("\nA").append(index).append("：").append(turn.answer());
            index++;
        }
        prompt.append("\n\n当前问题：").append(question);
        return prompt.toString();
    }

    private void appendSessionContext(Long userId, Long documentId, String sessionId, String question, String answer) {
        String sessionKey = CommonConstants.buildQaSessionContextKey(userId, documentId, sessionId);
        try {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("question", question);
            node.put("answer", answer);
            node.put("createTime", OffsetDateTime.now().toString());
            String payload = node.toString();

            ListOperations<String, String> listOps = stringRedisTemplate.opsForList();
            listOps.rightPush(sessionKey, payload);
            listOps.trim(sessionKey, -CommonConstants.QA_SESSION_MAX_CONTEXT_TURNS, -1);
            stringRedisTemplate.expire(sessionKey, CommonConstants.QA_SESSION_CONTEXT_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception ex) {
            log.warn("Failed to persist QA session context, sessionKey={}", sessionKey, ex);
        }
    }

    private List<SessionTurn> loadSessionTurns(Long userId, Long documentId, String sessionId) {
        String sessionKey = CommonConstants.buildQaSessionContextKey(userId, documentId, sessionId);
        try {
            List<String> values = stringRedisTemplate.opsForList().range(
                    sessionKey,
                    -CommonConstants.QA_SESSION_MAX_CONTEXT_TURNS,
                    -1
            );
            if (values == null || values.isEmpty()) {
                return List.of();
            }

            List<SessionTurn> turns = new ArrayList<>();
            for (String value : values) {
                if (value == null || value.isBlank()) {
                    continue;
                }
                try {
                    JsonNode node = objectMapper.readTree(value);
                    String q = node.path("question").asText("").trim();
                    String a = node.path("answer").asText("").trim();
                    if (!q.isEmpty() && !a.isEmpty()) {
                        turns.add(new SessionTurn(q, a));
                    }
                } catch (Exception ignored) {
                    // skip malformed entry
                }
            }
            return turns;
        } catch (Exception ex) {
            log.warn("Failed to read QA session context, sessionKey={}", sessionKey, ex);
            return List.of();
        }
    }

    private String normalizeSessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return CommonConstants.QA_DEFAULT_SESSION_ID;
        }
        return sessionId.trim();
    }

    private String buildDocumentContext(List<RankedChunk> rankedChunks, String normalizedContent) {
        if (rankedChunks.stream().noneMatch(chunk -> chunk.score() > 0)) {
            return truncateContext(normalizedContent);
        }
        if (rankedChunks.isEmpty()) {
            return "";
        }

        int maxLength = resolveMaxDocumentContextLength();
        StringBuilder builder = new StringBuilder(maxLength);
        for (RankedChunk rankedChunk : rankedChunks) {
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(rankedChunk.chunk().text());
            if (builder.length() >= maxLength) {
                break;
            }
        }

        String result = builder.toString();
        if (result.length() <= maxLength) {
            return result;
        }
        return result.substring(0, maxLength);
    }

    private String normalizeWhitespace(String content) {
        return content.replaceAll("\\s+", " ").trim();
    }

    private List<DocumentChunk> splitIntoChunks(String content) {
        if (content.isEmpty()) {
            return List.of();
        }

        List<DocumentChunk> chunks = new ArrayList<>();
        int chunkSize = Math.min(DEFAULT_CHUNK_SIZE, resolveMaxDocumentContextLength());
        int step = Math.max(1, chunkSize - DEFAULT_CHUNK_OVERLAP);

        int chunkIndex = 0;
        for (int start = 0; start < content.length(); start += step) {
            int end = Math.min(start + chunkSize, content.length());
            String text = content.substring(start, end).trim();
            if (!text.isEmpty()) {
                chunks.add(new DocumentChunk(chunkIndex++, start, end, text));
            }
            if (end >= content.length()) {
                break;
            }
        }
        return chunks;
    }

    private String truncateContext(String content) {
        int maxLength = resolveMaxDocumentContextLength();
        if (content.length() <= maxLength) {
            return content;
        }
        return content.substring(0, maxLength);
    }

    private List<String> extractQueryTerms(String question) {
        List<String> terms = new ArrayList<>();
        String[] split = QUERY_SPLIT_PATTERN.split(question.toLowerCase(Locale.ROOT));
        for (String term : split) {
            if (!term.isBlank()) {
                terms.add(term);
            }
        }
        if (terms.isEmpty()) {
            terms.add(question.toLowerCase(Locale.ROOT));
        }
        return terms;
    }

    private List<RankedChunk> rankChunks(List<DocumentChunk> chunks, List<String> terms) {
        if (chunks.isEmpty()) {
            return List.of();
        }

        List<RankedChunk> ranked = new ArrayList<>();
        for (DocumentChunk chunk : chunks) {
            ranked.add(new RankedChunk(chunk, scoreChunk(chunk.text(), terms)));
        }

        List<RankedChunk> positive = ranked.stream()
                .filter(chunk -> chunk.score() > 0)
                .sorted(Comparator.comparingInt(RankedChunk::score)
                        .reversed()
                        .thenComparingInt(chunk -> chunk.chunk().chunkIndex()))
                .limit(MAX_CITATION_COUNT)
                .toList();

        if (!positive.isEmpty()) {
            return positive;
        }

        return ranked.stream()
                .sorted(Comparator.comparingInt(chunk -> chunk.chunk().chunkIndex()))
                .limit(MAX_CITATION_COUNT)
                .toList();
    }

    private int scoreChunk(String text, List<String> terms) {
        String lowerText = text.toLowerCase(Locale.ROOT);
        int score = 0;
        for (String term : terms) {
            if (term.isBlank()) {
                continue;
            }
            if (lowerText.contains(term)) {
                score += Math.min(term.length(), 8);
            }
        }
        return score;
    }

    private List<DocumentQaResponse.CitationItem> buildCitations(List<RankedChunk> rankedChunks) {
        if (rankedChunks.isEmpty()) {
            return List.of();
        }

        List<DocumentQaResponse.CitationItem> citations = new ArrayList<>();
        for (RankedChunk ranked : rankedChunks.stream().limit(MAX_CITATION_COUNT).toList()) {
            DocumentChunk chunk = ranked.chunk();
            DocumentQaResponse.CitationItem item = new DocumentQaResponse.CitationItem();
            item.setChunkIndex(chunk.chunkIndex());
            item.setCharStart(chunk.startOffset());
            item.setCharEnd(chunk.endOffset());
            item.setScore(ranked.score());
            item.setSnippet(truncateSnippet(chunk.text()));
            citations.add(item);
        }
        return citations;
    }

    private String truncateSnippet(String text) {
        if (text.length() <= MAX_CITATION_SNIPPET_LENGTH) {
            return text;
        }
        return text.substring(0, MAX_CITATION_SNIPPET_LENGTH) + "...";
    }

    private int resolveMaxDocumentContextLength() {
        if (maxDocumentContextLength <= 0) {
            return DEFAULT_MAX_DOCUMENT_CONTEXT_LENGTH;
        }
        return maxDocumentContextLength;
    }

    private int resolveHistoryLimit(Integer limit) {
        if (limit == null || limit < 1) {
            return DEFAULT_HISTORY_LIMIT;
        }
        return Math.min(limit, MAX_HISTORY_LIMIT);
    }

    private String getCachedAnswer(String cacheKey) {
        try {
            String value = stringRedisTemplate.opsForValue().get(cacheKey);
            if (value == null || value.isBlank()) {
                return null;
            }
            return value;
        } catch (Exception ex) {
            log.warn("Failed to read QA answer cache, fallback to AI, cacheKey={}", cacheKey, ex);
            return null;
        }
    }

    private void cacheAnswer(String cacheKey, String answer) {
        try {
            stringRedisTemplate.opsForValue().set(
                    cacheKey,
                    answer,
                    CommonConstants.QA_ANSWER_CACHE_TTL_SECONDS,
                    TimeUnit.SECONDS
            );
        } catch (Exception ex) {
            log.warn("Failed to write QA answer cache, ignored, cacheKey={}", cacheKey, ex);
        }
    }

    private String resolveDocumentVersion(LocalDateTime updateTime) {
        if (updateTime == null) {
            return "0";
        }
        return updateTime.toString();
    }

    private String hashQuestion(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            return Integer.toHexString(input.hashCode());
        }
    }

    private void checkQaRateLimit(Long userId) {
        ValidationUtils.requireNonNull(userId, "userId");

        boolean allowed = redisTokenBucketRateLimiter.tryConsume(
                CommonConstants.buildAiQaRateLimitKey(userId),
                CommonConstants.AI_QA_TOKEN_BUCKET_CAPACITY,
                CommonConstants.AI_QA_TOKEN_BUCKET_REFILL_TOKENS,
                CommonConstants.AI_QA_TOKEN_BUCKET_REFILL_INTERVAL_SECONDS
        );
        if (allowed) {
            return;
        }

        DocPilotMetrics.recordRateLimitTrigger("ai_qa");
        throw new BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED, "问答请求过于频繁，请稍后再试");
    }

    private record DocumentChunk(int chunkIndex, int startOffset, int endOffset, String text) {
    }

    private record RankedChunk(DocumentChunk chunk, int score) {
    }

    private record SessionTurn(String question, String answer) {
    }

    private record QaContext(Long documentId,
                             String question,
                             String documentContext,
                             String documentVersion,
                             List<DocumentQaResponse.CitationItem> citations,
                             String sessionId) {
        private QaContext {
            citations = List.copyOf(Objects.requireNonNull(citations));
        }
    }
}
