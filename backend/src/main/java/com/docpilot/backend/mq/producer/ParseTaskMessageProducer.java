package com.docpilot.backend.mq.producer;

import com.docpilot.backend.mq.message.ParseTaskMessage;

public interface ParseTaskMessageProducer {

    void sendParseTaskCreated(ParseTaskMessage message);
}

