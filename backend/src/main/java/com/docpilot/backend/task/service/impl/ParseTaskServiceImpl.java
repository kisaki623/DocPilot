package com.docpilot.backend.task.service.impl;

import com.docpilot.backend.common.constant.CommonConstants;
import com.docpilot.backend.common.constant.ParseStatusConstants;
import com.docpilot.backend.common.error.ErrorCode;
import com.docpilot.backend.common.exception.BusinessException;
import com.docpilot.backend.common.metrics.DocPilotMetrics;
import com.docpilot.backend.common.util.ValidationUtils;
import com.docpilot.backend.document.constant.DocumentStatus;
import com.docpilot.backend.document.entity.Document;
import com.docpilot.backend.document.mapper.DocumentMapper;
import com.docpilot.backend.mq.entity.ParseTaskConsumeRecord;
import com.docpilot.backend.mq.entity.ParseTaskOutboxMessage;
import com.docpilot.backend.mq.mapper.ParseTaskConsumeRecordMapper;
import com.docpilot.backend.mq.mapper.ParseTaskOutboxMessageMapper;
import com.docpilot.backend.mq.service.ParseTaskOutboxRelayService;
import com.docpilot.backend.task.entity.ParseTask;
import com.docpilot.backend.task.mapper.ParseTaskMapper;
import com.docpilot.backend.task.service.ParseTaskService;
import com.docpilot.backend.task.vo.ParseTaskCreateResponse;
import com.docpilot.backend.task.vo.ParseTaskStatusResponse;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Service
public class ParseTaskServiceImpl implements ParseTaskService {

    private static final Logger log = LoggerFactory.getLogger(ParseTaskServiceImpl.class);
    private static final long PARSE_TASK_LOCK_WAIT_SECONDS = 0L;
    private static final String RECOVERY_NONE = "NONE";
    private static final String RECOVERY_WAIT_OR_REFRESH = "WAIT_OR_REFRESH";
    private static final String RECOVERY_REPARSE_AVAILABLE = "REPARSE_AVAILABLE";
    private static final String RECOVERY_RETRY_OR_REPARSE_REQUIRED = "RETRY_OR_REPARSE_REQUIRED";
    private static final String RECOVERY_STALE_RECONCILIATION_PENDING = "STALE_RECONCILIATION_PENDING";

    private final ParseTaskMapper parseTaskMapper;
    private final DocumentMapper documentMapper;
    private final ParseTaskConsumeRecordMapper consumeRecordMapper;
    private final ParseTaskOutboxMessageMapper outboxMessageMapper;
    private final ParseTaskOutboxRelayService parseTaskOutboxRelayService;
    private final RedissonClient redissonClient;
    private final StringRedisTemplate stringRedisTemplate;

    @Value("${app.redisson.parse-task-lock-fail-message:当前文档解析任务处理中，请稍后重试}")
    private String parseTaskLockFailMessage;

    @Value("${app.parse-task.recovery.stale-timeout-minutes:30}")
    private long recoveryStaleTimeoutMinutes;

    public ParseTaskServiceImpl(ParseTaskMapper parseTaskMapper,
                                DocumentMapper documentMapper,
                                ParseTaskConsumeRecordMapper consumeRecordMapper,
                                ParseTaskOutboxMessageMapper outboxMessageMapper,
                                ParseTaskOutboxRelayService parseTaskOutboxRelayService,
                                RedissonClient redissonClient,
                                StringRedisTemplate stringRedisTemplate) {
        this.parseTaskMapper = parseTaskMapper;
        this.documentMapper = documentMapper;
        this.consumeRecordMapper = consumeRecordMapper;
        this.outboxMessageMapper = outboxMessageMapper;
        this.parseTaskOutboxRelayService = parseTaskOutboxRelayService;
        this.redissonClient = redissonClient;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ParseTaskCreateResponse create(Long documentId, Long userId) {
        ValidationUtils.requireNonNull(documentId, "documentId");
        ValidationUtils.requireNonNull(userId, "userId");

        String lockKey = CommonConstants.buildParseTaskMutationLockKey(userId, documentId);
        RLock lock = redissonClient.getLock(lockKey);
        if (!tryLock(lock, "create")) {
            throw new BusinessException(ErrorCode.PARSE_TASK_LOCKED, parseTaskLockFailMessage);
        }

        try {
            return doCreate(documentId, userId);
        } finally {
            unlock(lockKey, lock);
        }
    }

    private ParseTaskCreateResponse doCreate(Long documentId, Long userId) {
        Document document = ensureOwnedDocument(documentId, userId);

        ParseTask existingTask = parseTaskMapper.selectLatestByUserAndDocumentId(userId, documentId);
        if (existingTask != null) {
            if (ParseStatusConstants.isRetryAllowed(existingTask.getStatus())) {
                throw new BusinessException(ErrorCode.PARSE_TASK_RETRY_NOT_ALLOWED, "任务已失败，请调用重试接口重新触发解析");
            }
            return toResponse(existingTask, true);
        }

        ParseTask parseTask = new ParseTask();
        parseTask.setUserId(userId);
        parseTask.setDocumentId(documentId);
        parseTask.setFileRecordId(document.getFileRecordId());
        parseTask.setStatus(ParseStatusConstants.PENDING);

        try {
            parseTaskMapper.insert(parseTask);
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.PARSE_TASK_CREATE_FAILED, "解析任务创建失败");
        }

        Long outboxId = parseTaskOutboxRelayService.appendPending(
                parseTask.getId(),
                parseTask.getDocumentId(),
                parseTask.getFileRecordId(),
                "create"
        );
        dispatchOutboxAfterCommit(outboxId);

        return toResponse(parseTask, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ParseTaskCreateResponse retry(Long documentId, Long userId) {
        ValidationUtils.requireNonNull(documentId, "documentId");
        ValidationUtils.requireNonNull(userId, "userId");

        String lockKey = CommonConstants.buildParseTaskMutationLockKey(userId, documentId);
        RLock lock = redissonClient.getLock(lockKey);
        if (!tryLock(lock, "retry")) {
            throw new BusinessException(ErrorCode.PARSE_TASK_LOCKED, parseTaskLockFailMessage);
        }

        try {
            return doRetry(documentId, userId);
        } finally {
            unlock(lockKey, lock);
        }
    }

    private ParseTaskCreateResponse doRetry(Long documentId, Long userId) {
        Document document = ensureOwnedDocument(documentId, userId);

        ParseTask latestTask = parseTaskMapper.selectLatestByUserAndDocumentId(userId, documentId);
        if (latestTask == null) {
            throw new BusinessException(ErrorCode.PARSE_TASK_NOT_FOUND, "未找到可重试的解析任务");
        }
        if (!ParseStatusConstants.isRetryAllowed(latestTask.getStatus())) {
            throw new BusinessException(ErrorCode.PARSE_TASK_RETRY_NOT_ALLOWED,
                    "仅 FAILED 状态任务允许重试，当前状态: " + latestTask.getStatus());
        }

        int nextRetryCount = resolveRetryCount(latestTask.getRetryCount()) + 1;
        int updatedRows = parseTaskMapper.resetFailedTaskForRetry(latestTask.getId(), userId, nextRetryCount);
        if (updatedRows <= 0) {
            throw new BusinessException(ErrorCode.PARSE_TASK_RETRY_NOT_ALLOWED, "任务状态已变化，请刷新后重试");
        }

        Document retryDocument = new Document();
        retryDocument.setId(document.getId());
        retryDocument.setSummary(null);
        retryDocument.setContent(null);
        retryDocument.setParseStatus(ParseStatusConstants.PENDING);
        documentMapper.updateById(retryDocument);
        evictDocumentDetailCache(userId, documentId);

        latestTask.setStatus(ParseStatusConstants.PENDING);
        latestTask.setErrorMsg(null);
        latestTask.setStartTime(null);
        latestTask.setFinishTime(null);
        latestTask.setRetryCount(nextRetryCount);

        Long outboxId = parseTaskOutboxRelayService.appendPending(
                latestTask.getId(),
                latestTask.getDocumentId(),
                latestTask.getFileRecordId(),
                "retry"
        );
        dispatchOutboxAfterCommit(outboxId);

        return toResponse(latestTask, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ParseTaskCreateResponse reparse(Long documentId, Long userId) {
        ValidationUtils.requireNonNull(documentId, "documentId");
        ValidationUtils.requireNonNull(userId, "userId");

        String lockKey = CommonConstants.buildParseTaskMutationLockKey(userId, documentId);
        RLock lock = redissonClient.getLock(lockKey);
        if (!tryLock(lock, "reparse")) {
            throw new BusinessException(ErrorCode.PARSE_TASK_LOCKED, parseTaskLockFailMessage);
        }

        try {
            return doReparse(documentId, userId);
        } finally {
            unlock(lockKey, lock);
        }
    }

    private ParseTaskCreateResponse doReparse(Long documentId, Long userId) {
        Document document = ensureOwnedDocument(documentId, userId);

        ParseTask latestTask = parseTaskMapper.selectLatestByUserAndDocumentId(userId, documentId);
        if (latestTask == null) {
            throw new BusinessException(ErrorCode.PARSE_TASK_NOT_FOUND, "未找到可重新解析的任务，请先创建解析任务");
        }
        if (!ParseStatusConstants.isReparseAllowed(latestTask.getStatus())) {
            throw new BusinessException(
                    ErrorCode.PARSE_TASK_REPARSE_NOT_ALLOWED,
                    "仅 SUCCESS 或 FAILED 状态任务允许重新解析，当前状态: " + latestTask.getStatus()
            );
        }

        int updatedRows = parseTaskMapper.resetTerminalTaskForReparse(latestTask.getId(), userId);
        if (updatedRows <= 0) {
            throw new BusinessException(ErrorCode.PARSE_TASK_REPARSE_NOT_ALLOWED, "任务状态已变化，请刷新后重试");
        }

        Document reparseDocument = new Document();
        reparseDocument.setId(document.getId());
        reparseDocument.setSummary(null);
        reparseDocument.setContent(null);
        reparseDocument.setParseStatus(ParseStatusConstants.PENDING);
        documentMapper.updateById(reparseDocument);
        evictDocumentDetailCache(userId, documentId);

        latestTask.setStatus(ParseStatusConstants.PENDING);
        latestTask.setErrorMsg(null);
        latestTask.setStartTime(null);
        latestTask.setFinishTime(null);

        Long outboxId = parseTaskOutboxRelayService.appendPending(
                latestTask.getId(),
                latestTask.getDocumentId(),
                latestTask.getFileRecordId(),
                "reparse"
        );
        dispatchOutboxAfterCommit(outboxId);

        return toResponse(latestTask, false);
    }

    @Override
    public ParseTaskStatusResponse status(Long documentId, Long userId) {
        ValidationUtils.requireNonNull(documentId, "documentId");
        ValidationUtils.requireNonNull(userId, "userId");

        Document document = ensureOwnedDocument(documentId, userId);
        ParseTask latestTask = parseTaskMapper.selectLatestByUserAndDocumentId(userId, documentId);
        if (latestTask == null) {
            throw new BusinessException(ErrorCode.PARSE_TASK_NOT_FOUND, "未找到解析任务");
        }
        return toStatusResponse(latestTask, document);
    }

    private boolean tryLock(RLock lock, String operation) {
        try {
            // No explicit lease time: Redisson WatchDog auto-renews while thread is alive.
            boolean locked = lock.tryLock(PARSE_TASK_LOCK_WAIT_SECONDS, TimeUnit.SECONDS);
            DocPilotMetrics.recordLockCompetition(operation, locked ? "success" : "failed");
            return locked;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            DocPilotMetrics.recordLockCompetition(operation, "interrupted");
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "解析任务锁获取被中断");
        }
    }

    private void unlock(String lockKey, RLock lock) {
        try {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        } catch (Exception ex) {
            log.warn("解析任务锁释放失败，lockKey={}", lockKey, ex);
        }
    }


    private int resolveRetryCount(Integer retryCount) {
        if (retryCount == null || retryCount < 0) {
            return 0;
        }
        return retryCount;
    }

    private Document ensureOwnedDocument(Long documentId, Long userId) {
        Document document = documentMapper.selectById(documentId);
        if (document == null) {
            throw new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND, "文档不存在");
        }
        if (!userId.equals(document.getUserId())) {
            throw new BusinessException(ErrorCode.DOCUMENT_FORBIDDEN, "当前用户无权访问该文档");
        }
        if (DocumentStatus.isRemoved(document.getStatus())) {
            throw new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND, "document does not exist");
        }
        return document;
    }

    private void evictDocumentDetailCache(Long userId, Long documentId) {
        if (userId == null || documentId == null) {
            return;
        }
        String cacheKey = CommonConstants.buildDocumentDetailCacheKey(userId, documentId);
        try {
            stringRedisTemplate.delete(cacheKey);
        } catch (Exception ex) {
            log.warn("文档详情缓存失效失败，cacheKey={}", cacheKey, ex);
        }
    }

    private void dispatchOutboxAfterCommit(Long outboxId) {
        if (outboxId == null) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    parseTaskOutboxRelayService.dispatchByOutboxId(outboxId, "immediate");
                }
            });
            return;
        }
        parseTaskOutboxRelayService.dispatchByOutboxId(outboxId, "immediate");
    }

    private ParseTaskCreateResponse toResponse(ParseTask parseTask, boolean reused) {
        ParseTaskCreateResponse response = new ParseTaskCreateResponse();
        response.setTaskId(parseTask.getId());
        response.setUserId(parseTask.getUserId());
        response.setDocumentId(parseTask.getDocumentId());
        response.setFileRecordId(parseTask.getFileRecordId());
        response.setStatus(parseTask.getStatus());
        response.setStatusLabel(ParseStatusConstants.toLabel(parseTask.getStatus()));
        response.setStatusDescription(ParseStatusConstants.toStageDescription(parseTask.getStatus()));
        response.setReused(reused);
        response.setRetryCount(resolveRetryCount(parseTask.getRetryCount()));
        response.setErrorMsg(parseTask.getErrorMsg());
        response.setStartTime(parseTask.getStartTime());
        response.setFinishTime(parseTask.getFinishTime());
        return response;
    }

    private ParseTaskStatusResponse toStatusResponse(ParseTask parseTask, Document document) {
        String status = parseTask.getStatus();
        String errorCode = extractErrorCode(parseTask.getErrorMsg());
        String failedStage = extractFailedStage(parseTask.getErrorMsg());
        boolean stale = isStaleProcessing(parseTask);
        String staleReason = resolveStaleReason(parseTask, stale);
        ParseTaskConsumeRecord consumeRecord = consumeRecordMapper.selectLatestByTaskId(parseTask.getId());
        ParseTaskOutboxMessage outboxMessage = outboxMessageMapper.selectLatestByTaskId(parseTask.getId());

        ParseTaskStatusResponse response = new ParseTaskStatusResponse();
        response.setTaskId(parseTask.getId());
        response.setUserId(parseTask.getUserId());
        response.setDocumentId(parseTask.getDocumentId());
        response.setFileRecordId(parseTask.getFileRecordId());
        response.setStatus(status);
        response.setStatusLabel(ParseStatusConstants.toLabel(status));
        response.setStatusDescription(ParseStatusConstants.toStageDescription(status));
        response.setDocumentParseStatus(document.getParseStatus());
        response.setTerminal(ParseStatusConstants.isTerminal(status));
        response.setProcessing(ParseStatusConstants.isProcessingStage(status));
        response.setRetryAllowed(ParseStatusConstants.isRetryAllowed(status));
        response.setReparseAllowed(ParseStatusConstants.isReparseAllowed(status));
        response.setSafeReindexAllowed(false);
        response.setContentOnlyReindexAllowed(false);
        response.setParsedContentPresent(document.getContent() != null && !document.getContent().isBlank());
        response.setStale(stale);
        response.setStaleReason(staleReason);
        response.setConsumeStatus(consumeRecord == null ? null : consumeRecord.getStatus());
        response.setOutboxStatus(outboxMessage == null ? null : outboxMessage.getStatus());
        response.setOutboxRetryCount(outboxMessage == null ? null : resolveRetryCount(outboxMessage.getRetryCount()));
        response.setOutboxNextRetryTime(outboxMessage == null ? null : outboxMessage.getNextRetryTime());
        response.setErrorCode(errorCode);
        response.setFailedStage(failedStage);
        response.setRecoveryAction(resolveRecoveryAction(status, stale));
        response.setRecoveryDescription(resolveRecoveryDescription(status, errorCode, staleReason));
        response.setRetryCount(resolveRetryCount(parseTask.getRetryCount()));
        response.setErrorMsg(parseTask.getErrorMsg());
        response.setStartTime(parseTask.getStartTime());
        response.setFinishTime(parseTask.getFinishTime());
        response.setUpdateTime(parseTask.getUpdateTime());
        return response;
    }

    private String resolveRecoveryAction(String status, boolean stale) {
        if (stale) {
            return RECOVERY_STALE_RECONCILIATION_PENDING;
        }
        if (ParseStatusConstants.FAILED.equals(status)) {
            return RECOVERY_RETRY_OR_REPARSE_REQUIRED;
        }
        if (ParseStatusConstants.SUCCESS.equals(status)) {
            return RECOVERY_REPARSE_AVAILABLE;
        }
        if (ParseStatusConstants.isProcessingStage(status) || ParseStatusConstants.PENDING.equals(status)) {
            return RECOVERY_WAIT_OR_REFRESH;
        }
        return RECOVERY_NONE;
    }

    private String resolveRecoveryDescription(String status, String errorCode, String staleReason) {
        if (staleReason != null && !staleReason.isBlank()) {
            return "任务可能已长时间停留在处理中状态；系统恢复扫描会将其安全收口为 FAILED，之后请通过 retry/reparse 重新解析原文件，禁止仅基于 Document.content 重建结构化索引";
        }
        if (ParseStatusConstants.FAILED.equals(status)) {
            if (errorCode != null && errorCode.startsWith("RAG_INDEX_")) {
                return "索引阶段失败；请通过 retry/reparse 重新解析原文件并携带 parser block metadata 重建索引，禁止仅基于 Document.content 重建结构化索引";
            }
            return "解析任务失败；请通过 retry/reparse 重新消费原文件，保留 parser metadata 后再进入索引";
        }
        if (ParseStatusConstants.SUCCESS.equals(status)) {
            return "任务已完成；如需刷新结构化索引，请使用 reparse 重新解析原文件";
        }
        if (ParseStatusConstants.isProcessingStage(status) || ParseStatusConstants.PENDING.equals(status)) {
            return "任务仍在解析或索引链路中，请等待或刷新状态";
        }
        return "当前状态无需恢复动作";
    }

    private boolean isStaleProcessing(ParseTask parseTask) {
        if (parseTask == null || (!ParseStatusConstants.isProcessingStage(parseTask.getStatus())
                && !ParseStatusConstants.PENDING.equals(parseTask.getStatus()))) {
            return false;
        }
        LocalDateTime updateTime = parseTask.getUpdateTime();
        if (updateTime == null) {
            return false;
        }
        long timeoutMinutes = Math.max(1L, recoveryStaleTimeoutMinutes);
        return updateTime.isBefore(LocalDateTime.now().minusMinutes(timeoutMinutes));
    }

    private String resolveStaleReason(ParseTask parseTask, boolean stale) {
        if (!stale || parseTask == null) {
            return null;
        }
        return "parse_task_" + parseTask.getStatus().toLowerCase() + "_timeout";
    }

    private String extractErrorCode(String errorMsg) {
        if (errorMsg == null || errorMsg.isBlank()) {
            return null;
        }
        String trimmed = errorMsg.trim();
        int stageIndex = trimmed.indexOf(" [stage=");
        if (stageIndex > 0) {
            return trimmed.substring(0, stageIndex);
        }
        int colonIndex = trimmed.indexOf(':');
        if (colonIndex > 0) {
            return trimmed.substring(0, colonIndex).trim();
        }
        int spaceIndex = trimmed.indexOf(' ');
        if (spaceIndex > 0) {
            return trimmed.substring(0, spaceIndex).trim();
        }
        return trimmed;
    }

    private String extractFailedStage(String errorMsg) {
        if (errorMsg == null || errorMsg.isBlank()) {
            return null;
        }
        String marker = "[stage=";
        int start = errorMsg.indexOf(marker);
        if (start < 0) {
            return null;
        }
        int valueStart = start + marker.length();
        int valueEnd = errorMsg.indexOf(']', valueStart);
        if (valueEnd <= valueStart) {
            return null;
        }
        return errorMsg.substring(valueStart, valueEnd);
    }
}

