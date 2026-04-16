package com.docpilot.backend.task.service.impl;

import com.docpilot.backend.common.constant.CommonConstants;
import com.docpilot.backend.common.constant.ParseStatusConstants;
import com.docpilot.backend.common.error.ErrorCode;
import com.docpilot.backend.common.exception.BusinessException;
import com.docpilot.backend.common.metrics.DocPilotMetrics;
import com.docpilot.backend.common.util.ValidationUtils;
import com.docpilot.backend.document.entity.Document;
import com.docpilot.backend.document.mapper.DocumentMapper;
import com.docpilot.backend.mq.service.ParseTaskOutboxRelayService;
import com.docpilot.backend.task.entity.ParseTask;
import com.docpilot.backend.task.mapper.ParseTaskMapper;
import com.docpilot.backend.task.service.ParseTaskService;
import com.docpilot.backend.task.vo.ParseTaskCreateResponse;
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

import java.util.concurrent.TimeUnit;

@Service
public class ParseTaskServiceImpl implements ParseTaskService {

    private static final Logger log = LoggerFactory.getLogger(ParseTaskServiceImpl.class);
    private static final long PARSE_TASK_LOCK_WAIT_SECONDS = 0L;

    private final ParseTaskMapper parseTaskMapper;
    private final DocumentMapper documentMapper;
    private final ParseTaskOutboxRelayService parseTaskOutboxRelayService;
    private final RedissonClient redissonClient;
    private final StringRedisTemplate stringRedisTemplate;

    @Value("${app.redisson.parse-task-lock-fail-message:当前文档解析任务处理中，请稍后重试}")
    private String parseTaskLockFailMessage;

    public ParseTaskServiceImpl(ParseTaskMapper parseTaskMapper,
                                DocumentMapper documentMapper,
                                ParseTaskOutboxRelayService parseTaskOutboxRelayService,
                                RedissonClient redissonClient,
                                StringRedisTemplate stringRedisTemplate) {
        this.parseTaskMapper = parseTaskMapper;
        this.documentMapper = documentMapper;
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
}

