package com.docpilot.backend.mq.job;

import com.docpilot.backend.mq.service.ParseTaskOutboxRelayService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ParseTaskOutboxScanJob {

    private static final Logger log = LoggerFactory.getLogger(ParseTaskOutboxScanJob.class);

    private final ParseTaskOutboxRelayService outboxRelayService;

    public ParseTaskOutboxScanJob(ParseTaskOutboxRelayService outboxRelayService) {
        this.outboxRelayService = outboxRelayService;
    }

    @Scheduled(fixedDelayString = "${app.rocketmq.outbox.scan-fixed-delay-ms:15000}")
    public void scanAndDispatch() {
        int successCount = outboxRelayService.dispatchDueMessages();
        if (successCount > 0) {
            log.info("解析任务 Outbox 补偿扫描完成，成功补发 {} 条消息", successCount);
        }
    }
}

