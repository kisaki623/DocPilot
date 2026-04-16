package com.docpilot.backend.common.constant;

public final class CommonConstants {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String AUTHORIZATION_HEADER = "Authorization";
    public static final String BEARER_PREFIX = "Bearer ";

    public static final int DEFAULT_PAGE_NUM = 1;
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    public static final String LOGIN_SMS_CODE_KEY_PREFIX = "docpilot:auth:sms:";
    public static final String LOGIN_TOKEN_KEY_PREFIX = "docpilot:auth:token:";
    public static final String DOCUMENT_DETAIL_CACHE_KEY_PREFIX = "docpilot:document:detail:";
    public static final String RATE_LIMIT_SMS_CODE_KEY_PREFIX = "docpilot:ratelimit:auth:code:";
    public static final String RATE_LIMIT_AI_QA_KEY_PREFIX = "docpilot:ratelimit:ai:qa:";
    public static final String RATE_LIMIT_FILE_UPLOAD_KEY_PREFIX = "docpilot:ratelimit:file:upload:";
    public static final String LOCK_PARSE_TASK_MUTATION_KEY_PREFIX = "docpilot:lock:task:mutation:";
    public static final String QA_ANSWER_CACHE_KEY_PREFIX = "docpilot:ai:answer:";
    public static final String QA_SESSION_CONTEXT_KEY_PREFIX = "docpilot:ai:session:";
    public static final String CHUNK_UPLOAD_META_KEY_PREFIX = "docpilot:file:chunk:meta:";
    public static final String CHUNK_UPLOAD_CHUNKS_KEY_PREFIX = "docpilot:file:chunk:parts:";
    public static final String CHUNK_UPLOAD_SESSION_INDEX_KEY_PREFIX = "docpilot:file:chunk:index:";
    public static final long LOGIN_SMS_CODE_TTL_SECONDS = 300L;
    public static final long LOGIN_TOKEN_TTL_SECONDS = 7L * 24 * 60 * 60;
    public static final long DOCUMENT_DETAIL_CACHE_TTL_SECONDS = 300L;
    public static final long SMS_CODE_RATE_LIMIT_WINDOW_SECONDS = 60L;
    public static final int SMS_CODE_RATE_LIMIT_MAX_REQUESTS = 1;
    public static final long AI_QA_RATE_LIMIT_WINDOW_SECONDS = 60L;
    public static final int AI_QA_RATE_LIMIT_MAX_REQUESTS = 5;
    public static final int AI_QA_TOKEN_BUCKET_CAPACITY = 5;
    public static final int AI_QA_TOKEN_BUCKET_REFILL_TOKENS = 1;
    public static final long AI_QA_TOKEN_BUCKET_REFILL_INTERVAL_SECONDS = 12L;
    public static final long FILE_UPLOAD_RATE_LIMIT_WINDOW_SECONDS = 60L;
    public static final int FILE_UPLOAD_RATE_LIMIT_MAX_REQUESTS = 3;
    public static final long PARSE_TASK_LOCK_TTL_SECONDS = 15L;
    public static final long QA_ANSWER_CACHE_TTL_SECONDS = 180L;
    public static final String QA_DEFAULT_SESSION_ID = "default";
    public static final int QA_SESSION_MAX_CONTEXT_TURNS = 6;
    public static final long QA_SESSION_CONTEXT_TTL_SECONDS = 1800L;
    public static final int QA_SESSION_CONTEXT_MAX_CHARS = 1200;

    public static String buildDocumentDetailCacheKey(Long userId, Long documentId) {
        return DOCUMENT_DETAIL_CACHE_KEY_PREFIX + "u:" + userId + ":d:" + documentId;
    }

    public static String buildSmsCodeRateLimitKey(String phone) {
        return RATE_LIMIT_SMS_CODE_KEY_PREFIX + phone;
    }

    public static String buildAiQaRateLimitKey(Long userId) {
        return RATE_LIMIT_AI_QA_KEY_PREFIX + "u:" + userId;
    }

    public static String buildFileUploadRateLimitKey(Long userId) {
        return RATE_LIMIT_FILE_UPLOAD_KEY_PREFIX + "u:" + userId;
    }

    public static String buildParseTaskMutationLockKey(Long userId, Long documentId) {
        return LOCK_PARSE_TASK_MUTATION_KEY_PREFIX + "u:" + userId + ":d:" + documentId;
    }

    public static String buildChunkUploadMetaKey(String uploadId) {
        return CHUNK_UPLOAD_META_KEY_PREFIX + uploadId;
    }

    public static String buildChunkUploadChunksKey(String uploadId) {
        return CHUNK_UPLOAD_CHUNKS_KEY_PREFIX + uploadId;
    }

    public static String buildChunkUploadSessionIndexKey(Long userId, String fileHash, Long fileSize) {
        return CHUNK_UPLOAD_SESSION_INDEX_KEY_PREFIX
                + "u:" + userId
                + ":h:" + fileHash
                + ":s:" + fileSize;
    }

    public static String buildQaAnswerCacheKey(Long userId,
                                               Long documentId,
                                               String documentVersion,
                                               String questionHash) {
        return QA_ANSWER_CACHE_KEY_PREFIX
                + "u:" + userId
                + ":d:" + documentId
                + ":v:" + documentVersion
                + ":q:" + questionHash;
    }

    public static String buildQaSessionContextKey(Long userId, Long documentId, String sessionId) {
        return QA_SESSION_CONTEXT_KEY_PREFIX
                + "u:" + userId
                + ":d:" + documentId
                + ":s:" + sessionId;
    }

    private CommonConstants() {
    }
}

