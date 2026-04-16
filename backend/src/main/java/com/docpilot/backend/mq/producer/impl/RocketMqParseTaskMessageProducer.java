package com.docpilot.backend.mq.producer.impl;

import com.docpilot.backend.mq.message.ParseTaskMessage;
import com.docpilot.backend.mq.producer.ParseTaskMessageProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.apache.rocketmq.spring.core.RocketMQTemplate;

@Component
@ConditionalOnProperty(prefix = "app.rocketmq", name = "enabled", havingValue = "true")
public class RocketMqParseTaskMessageProducer implements ParseTaskMessageProducer {

    private static final Logger log = LoggerFactory.getLogger(RocketMqParseTaskMessageProducer.class);

    private final RocketMQTemplate rocketMQTemplate;
    private final String parseTopic;

    public RocketMqParseTaskMessageProducer(
            RocketMQTemplate rocketMQTemplate,
            @Value("${app.rocketmq.parse-topic:docpilot-parse-topic}") String parseTopic) {
        this.rocketMQTemplate = rocketMQTemplate;
        this.parseTopic = parseTopic;
    }

    @Override
    public void sendParseTaskCreated(ParseTaskMessage message) {
        SendResult result = rocketMQTemplate.syncSend(parseTopic, MessageBuilder.withPayload(message).build());
        log.info("Parse task message sent. topic={}, messageKey={}, taskId={}, documentId={}, fileRecordId={}, sendStatus={}",
                parseTopic,
                message.getMessageKey(),
                message.getTaskId(),
                message.getDocumentId(),
                message.getFileRecordId(),
                result.getSendStatus());
    }
}

