package com.docpilot.backend.common.constant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParseStatusConstantsTest {

    @Test
    void shouldSupportStageFlowAndLegacyProcessingCompatibility() {
        assertTrue(ParseStatusConstants.canTransit(ParseStatusConstants.PENDING, ParseStatusConstants.UPLOADED));
        assertTrue(ParseStatusConstants.canTransit(ParseStatusConstants.UPLOADED, ParseStatusConstants.PARSING));
        assertTrue(ParseStatusConstants.canTransit(ParseStatusConstants.PARSING, ParseStatusConstants.SPLITTING));
        assertTrue(ParseStatusConstants.canTransit(ParseStatusConstants.SPLITTING, ParseStatusConstants.SUMMARIZING));
        assertTrue(ParseStatusConstants.canTransit(ParseStatusConstants.SUMMARIZING, ParseStatusConstants.INDEXING));
        assertTrue(ParseStatusConstants.canTransit(ParseStatusConstants.INDEXING, ParseStatusConstants.SUCCESS));

        // 兼容历史 PROCESSING 任务，允许迁移到新阶段。
        assertTrue(ParseStatusConstants.canTransit(ParseStatusConstants.PROCESSING, ParseStatusConstants.SPLITTING));
        assertEquals("解析中", ParseStatusConstants.toLabel(ParseStatusConstants.PROCESSING));
    }

    @Test
    void shouldRejectInvalidTransitionAndRestrictRetryToFailed() {
        assertFalse(ParseStatusConstants.canTransit(ParseStatusConstants.SUMMARIZING, ParseStatusConstants.UPLOADED));
        assertFalse(ParseStatusConstants.canTransit(ParseStatusConstants.SUCCESS, ParseStatusConstants.PARSING));

        assertTrue(ParseStatusConstants.isRetryAllowed(ParseStatusConstants.FAILED));
        assertFalse(ParseStatusConstants.isRetryAllowed(ParseStatusConstants.PARSING));
    }
}

