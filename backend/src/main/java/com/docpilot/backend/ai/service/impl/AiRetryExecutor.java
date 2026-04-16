package com.docpilot.backend.ai.service.impl;

import com.docpilot.backend.ai.exception.AiNonRetryableException;
import com.docpilot.backend.ai.exception.AiRetryableException;
import com.docpilot.backend.common.metrics.DocPilotMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.Locale;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

@Component
public class AiRetryExecutor {

    private static final Logger log = LoggerFactory.getLogger(AiRetryExecutor.class);

    private static final int DEFAULT_MAX_ATTEMPTS = 3;
    private static final long DEFAULT_INITIAL_BACKOFF_MS = 120L;
    private static final double DEFAULT_BACKOFF_MULTIPLIER = 2.0D;
    private static final long DEFAULT_MAX_BACKOFF_MS = 800L;

    @Value("${app.ai.retry.enabled:true}")
    private boolean retryEnabled;

    @Value("${app.ai.retry.max-attempts:3}")
    private int maxAttempts;

    @Value("${app.ai.retry.initial-backoff-ms:120}")
    private long initialBackoffMs;

    @Value("${app.ai.retry.backoff-multiplier:2.0}")
    private double backoffMultiplier;

    @Value("${app.ai.retry.max-backoff-ms:800}")
    private long maxBackoffMs;

    public <T> T execute(String scene, Supplier<T> supplier) {
        long startNanos = System.nanoTime();
        if (!retryEnabled) {
            try {
                T result = supplier.get();
                DocPilotMetrics.recordAiCall(scene, "success", System.nanoTime() - startNanos);
                return result;
            } catch (RuntimeException ex) {
                DocPilotMetrics.recordAiCall(scene, "failed", System.nanoTime() - startNanos);
                throw ex;
            }
        }

        int attempts = resolveMaxAttempts();
        long backoffMs = resolveInitialBackoffMs();
        RuntimeException lastException = null;

        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                T result = supplier.get();
                DocPilotMetrics.recordAiCall(scene, "success", System.nanoTime() - startNanos);
                return result;
            } catch (RuntimeException ex) {
                lastException = ex;
                if (!isRetryable(ex) || attempt >= attempts) {
                    DocPilotMetrics.recordAiCall(scene, "failed", System.nanoTime() - startNanos);
                    throw ex;
                }
                DocPilotMetrics.recordAiRetry(scene);
                log.warn("AI 调用失败，将执行重试。scene={}, attempt={}/{}, reason={}",
                        scene,
                        attempt,
                        attempts,
                        ex.getMessage());
                sleep(backoffMs);
                backoffMs = nextBackoff(backoffMs);
            }
        }

        throw lastException == null ? new IllegalStateException("AI 重试执行失败") : lastException;
    }

    // 对外保留可见性，便于单测验证异常分类。
    boolean isRetryable(Throwable throwable) {
        if (throwable == null) {
            return false;
        }
        if (throwable instanceof AiRetryableException) {
            return true;
        }
        if (throwable instanceof AiNonRetryableException) {
            return false;
        }
        if (throwable instanceof IllegalArgumentException
                || throwable instanceof UnsupportedOperationException
                || throwable instanceof NullPointerException) {
            return false;
        }
        if (throwable instanceof TimeoutException
                || throwable instanceof SocketTimeoutException
                || throwable instanceof ConnectException
                || throwable instanceof IOException) {
            return true;
        }

        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            return false;
        }

        String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains("timeout")
                || normalized.contains("timed out")
                || normalized.contains("temporarily")
                || normalized.contains("connection")
                || normalized.contains("network")
                || normalized.contains("5xx")
                || normalized.contains("503")
                || normalized.contains("429");
    }

    private int resolveMaxAttempts() {
        return Math.max(1, maxAttempts <= 0 ? DEFAULT_MAX_ATTEMPTS : maxAttempts);
    }

    private long resolveInitialBackoffMs() {
        return Math.max(1L, initialBackoffMs <= 0 ? DEFAULT_INITIAL_BACKOFF_MS : initialBackoffMs);
    }

    private long resolveMaxBackoffMs() {
        return Math.max(1L, maxBackoffMs <= 0 ? DEFAULT_MAX_BACKOFF_MS : maxBackoffMs);
    }

    private long nextBackoff(long currentBackoffMs) {
        double multiplier = backoffMultiplier <= 1.0D ? DEFAULT_BACKOFF_MULTIPLIER : backoffMultiplier;
        long next = (long) (currentBackoffMs * multiplier);
        return Math.min(next, resolveMaxBackoffMs());
    }

    private void sleep(long backoffMs) {
        try {
            Thread.sleep(backoffMs);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("AI 重试等待被中断", interruptedException);
        }
    }
}

