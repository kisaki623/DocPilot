package com.docpilot.backend.task.job;

import com.docpilot.backend.task.service.ParseTaskRecoveryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.parse-task.recovery", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ParseTaskRecoveryScanJob {

    private static final Logger log = LoggerFactory.getLogger(ParseTaskRecoveryScanJob.class);

    private final ParseTaskRecoveryService recoveryService;

    public ParseTaskRecoveryScanJob(ParseTaskRecoveryService recoveryService) {
        this.recoveryService = recoveryService;
    }

    @Scheduled(fixedDelayString = "${app.parse-task.recovery.scan-fixed-delay-ms:60000}")
    public void scanAndRecover() {
        int recovered = recoveryService.recoverDueTasks();
        if (recovered > 0) {
            log.warn("Parse task recovery scan completed. recovered={}", recovered);
        }
    }
}
