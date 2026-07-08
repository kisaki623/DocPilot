package com.docpilot.backend.ai.agent.tool.spec;

import com.docpilot.backend.ai.agent.tool.DocumentRagQaTool;
import com.docpilot.backend.ai.agent.tool.DocumentSearchTool;
import com.docpilot.backend.common.error.ErrorCode;
import com.docpilot.backend.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ToolArgumentValidatorTest {

    private final ToolArgumentValidator validator = new ToolArgumentValidator();

    @Test
    void shouldInjectCurrentUserAndNormalizeRagArguments() {
        Map<String, Object> result = validator.validate(7L, ragSpec(), Map.of(
                "documentId", "101",
                "question", "  cache?  ",
                "topK", 30,
                "indexVersion", "2",
                "sessionId", " s1 "
        ));

        assertThat(result)
                .containsEntry("userId", 7L)
                .containsEntry("documentId", 101L)
                .containsEntry("question", "cache?")
                .containsEntry("topK", ToolArgumentValidator.MAX_TOP_K)
                .containsEntry("indexVersion", 2)
                .containsEntry("sessionId", "s1");
    }

    @Test
    void shouldRejectUserIdMismatch() {
        BusinessException ex = assertThrows(BusinessException.class, () -> validator.validate(7L, ragSpec(), Map.of(
                "userId", 8L,
                "documentId", 101L,
                "question", "cache?"
        )));

        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
    }

    @Test
    void shouldRejectMissingRequiredField() {
        BusinessException ex = assertThrows(BusinessException.class, () -> validator.validate(7L, ragSpec(), Map.of(
                "documentId", 101L
        )));

        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
        assertThat(ex.getMessage()).contains("question");
    }

    @Test
    void shouldRejectBlankQuestion() {
        BusinessException ex = assertThrows(BusinessException.class, () -> validator.validate(7L, ragSpec(), Map.of(
                "documentId", 101L,
                "question", " "
        )));

        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
    }

    @Test
    void shouldRejectInvalidTopKAndIndexVersion() {
        assertThrows(BusinessException.class, () -> validator.validate(7L, ragSpec(), Map.of(
                "documentId", 101L,
                "question", "cache?",
                "topK", 0
        )));

        assertThrows(BusinessException.class, () -> validator.validate(7L, ragSpec(), Map.of(
                "documentId", 101L,
                "question", "cache?",
                "indexVersion", -1
        )));
    }

    @Test
    void shouldNormalizeSearchArguments() {
        Map<String, Object> result = validator.validate(7L, searchSpec(), Map.of(
                "documentId", "101",
                "query", "  cache?  ",
                "topK", 30,
                "indexVersion", "2"
        ));

        assertThat(result)
                .containsEntry("userId", 7L)
                .containsEntry("documentId", 101L)
                .containsEntry("query", "cache?")
                .containsEntry("topK", ToolArgumentValidator.MAX_TOP_K)
                .containsEntry("indexVersion", 2);
    }

    @Test
    void shouldRejectBlankSearchQuery() {
        BusinessException ex = assertThrows(BusinessException.class, () -> validator.validate(7L, searchSpec(), Map.of(
                "documentId", 101L,
                "query", " "
        )));

        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
        assertThat(ex.getMessage()).contains("query");
    }

    @Test
    void shouldRejectWrongTypes() {
        BusinessException ex = assertThrows(BusinessException.class, () -> validator.validate(7L, ragSpec(), Map.of(
                "documentId", "not-a-number",
                "question", "cache?"
        )));

        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
    }

    private ToolSpec ragSpec() {
        return new ToolSpec(
                DocumentRagQaTool.TOOL_NAME,
                "RAG QA",
                "RAG QA",
                ToolParameterSchema.object(Map.of(
                        "userId", "Long",
                        "documentId", "Long",
                        "question", "String",
                        "topK", "int|null",
                        "indexVersion", "int|null"
                )),
                Set.of("userId", "documentId", "question"),
                ToolResultSchema.object(Map.of("answer", "String")),
                ToolRiskLevel.MEDIUM,
                DocumentRagQaTool.TOOL_NAME,
                true
        );
    }

    private ToolSpec searchSpec() {
        return new ToolSpec(
                DocumentSearchTool.TOOL_NAME,
                "Document search",
                "Document search",
                ToolParameterSchema.object(Map.of(
                        "userId", "Long",
                        "documentId", "Long",
                        "query", "String",
                        "topK", "int|null",
                        "indexVersion", "int|null"
                )),
                Set.of("userId", "documentId", "query"),
                ToolResultSchema.object(Map.of("hits", "List")),
                ToolRiskLevel.MEDIUM,
                DocumentSearchTool.TOOL_NAME,
                true
        );
    }
}
