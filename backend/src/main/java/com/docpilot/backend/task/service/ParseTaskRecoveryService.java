package com.docpilot.backend.task.service;

import com.docpilot.backend.common.constant.CommonConstants;
import com.docpilot.backend.common.constant.ParseStatusConstants;
import com.docpilot.backend.document.entity.Document;
import com.docpilot.backend.document.mapper.DocumentMapper;
import com.docpilot.backend.mq.entity.ParseTaskOutboxMessage;
import com.docpilot.backend.mq.mapper.ParseTaskOutboxMessageMapper;
import com.docpilot.backend.task.entity.ParseTask;
import com.docpilot.backend.task.mapper.ParseTaskMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ParseTaskRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(ParseTaskRecoveryService.class);
    private static final int ERROR_MSG_MAX_LENGTH = 512;

    private final ParseTaskMapper parseTaskMapper;
    private final DocumentMapper documentMapper;
    private final ParseTaskOutboxMessageMapper outboxMessageMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final long staleTimeoutMinutes;
    private final int batchSize;
    private final int maxOutboxRetryCount;

    public ParseTaskRecoveryService(ParseTaskMapper parseTaskMapper,
                                    DocumentMapper documentMapper,
                                    ParseTaskOutboxMessageMapper outboxMessageMapper,
                                    StringRedisTemplate stringRedisTemplate,
                                    @Value("${app.parse-task.recovery.stale-timeout-minutes:30}") long staleTimeoutMinutes,
                                    @Value("${app.parse-task.recovery.batch-size:20}") int batchSize,
                                    @Value("${app.rocketmq.outbox.max-retry-count:20}") int maxOutboxRetryCount) {
        this.parseTaskMapper = parseTaskMapper;
        this.documentMapper = documentMapper;
        this.outboxMessageMapper = outboxMessageMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.staleTimeoutMinutes = Math.max(1L, staleTimeoutMinutes);
        this.batchSize = Math.max(1, batchSize);
        this.maxOutboxRetryCount = Math.max(1, maxOutboxRetryCount);
    }

    @Transactional(rollbackFor = Exception.class)
    public int recoverDueTasks() {
        return recoverStaleProcessingTasks() + recoverExhaustedOutboxTasks();
    }

    @Transactional(rollbackFor = Exception.class)
    public int recoverStaleProcessingTasks() {
        LocalDateTime staleBefore = LocalDateTime.now().minusMinutes(staleTimeoutMinutes);
        List<ParseTask> staleTasks = parseTaskMapper.selectStaleProcessingTasks(staleBefore, batchSize);
        int recovered = 0;
        for (ParseTask task : staleTasks) {
            if (markFailed(task, "PARSE_TASK_STALE_TIMEOUT", "task stayed in processing state too long")) {
                recovered++;
            }
        }
        return recovered;
    }

    @Transactional(rollbackFor = Exception.class)
    public int recoverExhaustedOutboxTasks() {
        List<ParseTaskOutboxMessage> exhaustedMessages = outboxMessageMapper.selectExhaustedFailed(maxOutboxRetryCount, batchSize);
        int recovered = 0;
        for (ParseTaskOutboxMessage message : exhaustedMessages) {
            ParseTask task = parseTaskMapper.selectById(message.getTaskId());
            if (task == null || ParseStatusConstants.isTerminal(task.getStatus())) {
                continue;
            }
            if (markFailed(task, "PARSE_OUTBOX_EXHAUSTED", "parse outbox dispatch retries exhausted")) {
                recovered++;
            }
        }
        return recovered;
    }

    private boolean markFailed(ParseTask task, String errorType, String errorMessage) {
        if (task == null || task.getId() == null || ParseStatusConstants.isTerminal(task.getStatus())) {
            return false;
        }
        String stage = (task.getStatus() == null || task.getStatus().isBlank()) ? "UNKNOWN" : task.getStatus();
        String safeError = limitError(errorType + " [stage=" + stage + "]: " + errorMessage);
        int updated = parseTaskMapper.markStaleTaskFailed(task.getId(), safeError, LocalDateTime.now());
        if (updated <= 0) {
            return false;
        }
        if (task.getDocumentId() != null) {
            Document document = new Document();
            document.setId(task.getDocumentId());
            document.setParseStatus(ParseStatusConstants.FAILED);
            documentMapper.updateById(document);
            evictDocumentDetailCache(task.getUserId(), task.getDocumentId());
        }
        log.warn("Parse task recovered by fail-closed policy. taskId={}, documentId={}, errorType={}",
                task.getId(), task.getDocumentId(), errorType);
        return true;
    }

    private void evictDocumentDetailCache(Long userId, Long documentId) {
        if (userId == null || documentId == null) {
            return;
        }
        try {
            stringRedisTemplate.delete(CommonConstants.buildDocumentDetailCacheKey(userId, documentId));
        } catch (Exception ex) {
            log.warn("Document detail cache eviction failed during parse recovery. userId={}, documentId={}",
                    userId, documentId, ex);
        }
    }

    private String limitError(String errorMessage) {
        if (errorMessage == null || errorMessage.isBlank()) {
            return "unknown error";
        }
        String trimmed = errorMessage.trim();
        if (trimmed.length() <= ERROR_MSG_MAX_LENGTH) {
            return trimmed;
        }
        return trimmed.substring(0, ERROR_MSG_MAX_LENGTH);
    }
}
