package com.docpilot.backend.mq.service;

import com.docpilot.backend.common.metrics.DocPilotMetrics;
import com.docpilot.backend.mq.entity.ParseTaskOutboxMessage;
import com.docpilot.backend.mq.mapper.ParseTaskOutboxMessageMapper;
import com.docpilot.backend.mq.message.ParseTaskMessage;
import com.docpilot.backend.mq.producer.ParseTaskMessageProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ParseTaskOutboxRelayService {

    private static final Logger log = LoggerFactory.getLogger(ParseTaskOutboxRelayService.class);
    private static final int ERROR_MSG_MAX_LENGTH = 512;

    private final ParseTaskOutboxMessageMapper outboxMessageMapper;
    private final ParseTaskMessageProducer parseTaskMessageProducer;

    @Value("${app.rocketmq.outbox.max-retry-count:20}")
    private int maxRetryCount;

    @Value("${app.rocketmq.outbox.retry-backoff-seconds:30}")
    private int retryBackoffSeconds;

    @Value("${app.rocketmq.outbox.scan-batch-size:20}")
    private int scanBatchSize;

    public ParseTaskOutboxRelayService(ParseTaskOutboxMessageMapper outboxMessageMapper,
                                       ParseTaskMessageProducer parseTaskMessageProducer) {
        this.outboxMessageMapper = outboxMessageMapper;
        this.parseTaskMessageProducer = parseTaskMessageProducer;
    }

    public Long appendPending(Long taskId, Long documentId, Long fileRecordId, String source) {
        ParseTaskOutboxMessage outboxMessage = new ParseTaskOutboxMessage();
        outboxMessage.setMessageKey(buildMessageKey(taskId, source));
        outboxMessage.setTaskId(taskId);
        outboxMessage.setDocumentId(documentId);
        outboxMessage.setFileRecordId(fileRecordId);
        outboxMessage.setStatus("PENDING");
        outboxMessage.setRetryCount(0);
        outboxMessage.setNextRetryTime(LocalDateTime.now());
        outboxMessageMapper.insert(outboxMessage);
        return outboxMessage.getId();
    }

    public boolean dispatchByOutboxId(Long outboxId) {
        return dispatchByOutboxId(outboxId, "unknown");
    }

    public boolean dispatchByOutboxId(Long outboxId, String trigger) {
        ParseTaskOutboxMessage outboxMessage = outboxMessageMapper.selectById(outboxId);
        if (outboxMessage == null || "SENT".equals(outboxMessage.getStatus())) {
            return true;
        }

        try {
            ParseTaskMessage message = new ParseTaskMessage();
            message.setMessageKey(outboxMessage.getMessageKey());
            message.setTaskId(outboxMessage.getTaskId());
            message.setDocumentId(outboxMessage.getDocumentId());
            message.setFileRecordId(outboxMessage.getFileRecordId());
            parseTaskMessageProducer.sendParseTaskCreated(message);

            outboxMessageMapper.markSent(outboxMessage.getId(), LocalDateTime.now());
            DocPilotMetrics.recordOutboxDispatch(trigger, "success");
            return true;
        } catch (Exception ex) {
            LocalDateTime nextRetryTime = LocalDateTime.now().plusSeconds(Math.max(1, retryBackoffSeconds));
            outboxMessageMapper.markFailed(outboxMessage.getId(), nextRetryTime, trimError(ex.getMessage()));
            DocPilotMetrics.recordOutboxDispatch(trigger, "failed");
            log.warn("解析任务 Outbox 发送失败，等待补偿重试。outboxId={}, taskId={}, messageKey={}",
                    outboxMessage.getId(), outboxMessage.getTaskId(), outboxMessage.getMessageKey(), ex);
            return false;
        }
    }

    public int dispatchDueMessages() {
        List<ParseTaskOutboxMessage> pendingMessages = outboxMessageMapper.selectDispatchable(
                LocalDateTime.now(),
                Math.max(1, scanBatchSize),
                Math.max(1, maxRetryCount)
        );

        int successCount = 0;
        for (ParseTaskOutboxMessage outboxMessage : pendingMessages) {
            if (dispatchByOutboxId(outboxMessage.getId(), "scan")) {
                successCount++;
            }
        }
        return successCount;
    }

    private String buildMessageKey(Long taskId, String source) {
        String resolvedSource = (source == null || source.isBlank()) ? "unknown" : source;
        return "parse-task:" + taskId + ":" + resolvedSource + ":" + UUID.randomUUID();
    }

    private String trimError(String errorMessage) {
        if (errorMessage == null || errorMessage.isBlank()) {
            return "MQ_SEND_FAILED";
        }
        String normalized = errorMessage.trim();
        if (normalized.length() <= ERROR_MSG_MAX_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, ERROR_MSG_MAX_LENGTH);
    }
}

