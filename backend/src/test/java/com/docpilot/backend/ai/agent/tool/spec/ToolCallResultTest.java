package com.docpilot.backend.ai.agent.tool.spec;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolCallResultTest {

    @Test
    void shouldBuildSuccessResult() {
        ToolCallResult result = ToolCallResult.success("document_status_tool", "ok", "done");

        assertTrue(result.success());
        assertEquals(ToolCallStatus.SUCCESS, result.status());
        assertEquals("ok", result.result());
        assertEquals("done", result.outputSummary());
        assertEquals("", result.errorType());
    }

    @Test
    void shouldBuildFailureResultFromExceptionWithoutRawMessage() {
        ToolCallResult result = ToolCallResult.failed("rag_qa_tool", new IllegalStateException("secret endpoint"));

        assertFalse(result.success());
        assertEquals(ToolCallStatus.FAILED, result.status());
        assertEquals("IllegalStateException", result.errorType());
        assertEquals("IllegalStateException", result.errorMessage());
    }

    @Test
    void contextShouldNormalizeBlankStringsAndExposeDocumentScope() {
        ToolExecutionContext context = new ToolExecutionContext(1L, 2L, "  ", " trace-1 ", 1, 3);

        assertTrue(context.hasDocumentScope());
        assertEquals("", context.sessionId());
        assertEquals("trace-1", context.traceId());
    }
}
