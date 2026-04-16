package com.docpilot.backend.mq.consumer;

import com.docpilot.backend.common.metrics.DocPilotMetrics;
import com.docpilot.backend.mq.message.ParseTaskMessage;
import com.docpilot.backend.task.service.ParseTaskConsumeEntryService;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.rocketmq", name = "enabled", havingValue = "true")
@RocketMQMessageListener(
        topic = "${app.rocketmq.parse-topic:docpilot-parse-topic}",
        consumerGroup = "${app.rocketmq.consumer-group:docpilot-parse-consumer-group}"
)
public class ParseTaskMessageConsumer implements RocketMQListener<ParseTaskMessage> {

    private static final Logger log = LoggerFactory.getLogger(ParseTaskMessageConsumer.class);

    private final ParseTaskConsumeEntryService parseTaskConsumeEntryService;

    public ParseTaskMessageConsumer(ParseTaskConsumeEntryService parseTaskConsumeEntryService) {
        this.parseTaskConsumeEntryService = parseTaskConsumeEntryService;
    }

    @Override
    public void onMessage(ParseTaskMessage message) {
        try {
            log.info("Receive parse task message. messageKey={}, taskId={}, documentId={}, fileRecordId={}",
                    message == null ? null : message.getMessageKey(),
                    message == null ? null : message.getTaskId(),
                    message == null ? null : message.getDocumentId(),
                    message == null ? null : message.getFileRecordId());
            parseTaskConsumeEntryService.handle(message);
            DocPilotMetrics.recordMqConsume("success");
            log.info("Consume parse task message success. taskId={}", message == null ? null : message.getTaskId());
        } catch (Exception ex) {
            DocPilotMetrics.recordMqConsume("failed");
            log.error("Consume parse task message failed. messageKey={}, taskId={}, documentId={}, fileRecordId={}",
                    message == null ? null : message.getMessageKey(),
                    message == null ? null : message.getTaskId(),
                    message == null ? null : message.getDocumentId(),
                    message == null ? null : message.getFileRecordId(),
                    ex);
            throw new RuntimeException("Consume parse task message failed", ex);
        }
    }
}

