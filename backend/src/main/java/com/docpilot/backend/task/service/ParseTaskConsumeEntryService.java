package com.docpilot.backend.task.service;

import com.docpilot.backend.mq.message.ParseTaskMessage;

public interface ParseTaskConsumeEntryService {

    void handle(ParseTaskMessage message);
}

