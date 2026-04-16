package com.docpilot.backend.mq.producer.impl;

import com.docpilot.backend.mq.message.ParseTaskMessage;
import com.docpilot.backend.mq.producer.ParseTaskMessageProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.rocketmq", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoopParseTaskMessageProducer implements ParseTaskMessageProducer {

    private static final Logger log = LoggerFactory.getLogger(NoopParseTaskMessageProducer.class);

    @Override
    public void sendParseTaskCreated(ParseTaskMessage message) {
        log.info("RocketMQ disabled. Skip parse task message send. messageKey={}, taskId={}, documentId={}, fileRecordId={}",
                message.getMessageKey(),
                message.getTaskId(),
                message.getDocumentId(),
                message.getFileRecordId());
    }
}

