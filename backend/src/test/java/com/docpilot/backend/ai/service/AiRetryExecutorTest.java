package com.docpilot.backend.ai.service;

import com.docpilot.backend.ai.exception.AiNonRetryableException;
import com.docpilot.backend.ai.exception.AiRetryableException;
import com.docpilot.backend.ai.service.impl.AiRetryExecutor;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiRetryExecutorTest {

    @Test
    void shouldRetryWhenExceptionIsRetryableAndThenSucceed() {
        AiRetryExecutor executor = buildExecutor();
        final int[] callCounter = {0};

        String result = executor.execute("test", () -> {
            callCounter[0]++;
            if (callCounter[0] < 3) {
                throw new IllegalStateException("timeout from provider");
            }
            return "ok";
        });

        assertEquals("ok", result);
        assertEquals(3, callCounter[0]);
    }

    @Test
    void shouldFailFastWhenExceptionIsNotRetryable() {
        AiRetryExecutor executor = buildExecutor();
        final int[] callCounter = {0};

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> executor.execute("test", () -> {
            callCounter[0]++;
            throw new IllegalArgumentException("bad request");
        }));

        assertEquals("bad request", ex.getMessage());
        assertEquals(1, callCounter[0]);
    }

    @Test
    void shouldClassifyRetryableByCommonMessageKeywords() {
        AiRetryExecutor executor = buildExecutor();

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> executor.execute("test", () -> {
                    throw new RuntimeException("temporary network timeout");
                }));

        assertTrue(ex.getMessage().contains("temporary"));
    }

    @Test
    void shouldRetryWhenExceptionIsExplicitAiRetryable() {
        AiRetryExecutor executor = buildExecutor();
        final int[] callCounter = {0};

        String result = executor.execute("test", () -> {
            callCounter[0]++;
            if (callCounter[0] < 2) {
                throw new AiRetryableException("temporary upstream unavailable");
            }
            return "ok";
        });

        assertEquals("ok", result);
        assertEquals(2, callCounter[0]);
    }

    @Test
    void shouldNotRetryWhenExceptionIsExplicitAiNonRetryable() {
        AiRetryExecutor executor = buildExecutor();
        final int[] callCounter = {0};

        assertThrows(AiNonRetryableException.class, () -> executor.execute("test", () -> {
            callCounter[0]++;
            throw new AiNonRetryableException("invalid auth key");
        }));

        assertEquals(1, callCounter[0]);
    }

    private AiRetryExecutor buildExecutor() {
        AiRetryExecutor executor = new AiRetryExecutor();
        ReflectionTestUtils.setField(executor, "retryEnabled", true);
        ReflectionTestUtils.setField(executor, "maxAttempts", 3);
        ReflectionTestUtils.setField(executor, "initialBackoffMs", 1L);
        ReflectionTestUtils.setField(executor, "backoffMultiplier", 2.0D);
        ReflectionTestUtils.setField(executor, "maxBackoffMs", 4L);
        return executor;
    }
}

