package com.docpilot.backend.ai.service;

import com.docpilot.backend.ai.agent.dto.DocumentAgentRequest;
import com.docpilot.backend.ai.agent.entity.AgentTask;
import com.docpilot.backend.ai.agent.service.AgentTaskPersistenceService;
import com.docpilot.backend.ai.agent.service.impl.DocumentAgentServiceImpl;
import com.docpilot.backend.ai.agent.tool.DocumentQaTool;
import com.docpilot.backend.ai.agent.tool.DocumentStatusTool;
import com.docpilot.backend.ai.agent.tool.DocumentSummaryTool;
import com.docpilot.backend.ai.vo.DocumentQaResponse;
import com.docpilot.backend.common.constant.ParseStatusConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentAgentServiceImplTest {

    @Mock
    private DocumentStatusTool documentStatusTool;

    @Mock
    private DocumentSummaryTool documentSummaryTool;

    @Mock
    private DocumentQaTool documentQaTool;

    @Mock
    private AgentTaskPersistenceService persistenceService;

    private DocumentAgentServiceImpl buildService() {
        return new DocumentAgentServiceImpl(documentStatusTool, documentSummaryTool, documentQaTool, persistenceService);
    }

    private void stubPersistenceTask() {
        AgentTask mockTask = new AgentTask();
        mockTask.setId(1001L);
        when(persistenceService.createTask(anyLong(), anyLong(), anyString(), any())).thenReturn(mockTask);
    }

    private void verifyPersistenceSuccess() {
        verify(persistenceService).createTask(anyLong(), anyLong(), anyString(), any());
        verify(persistenceService, atLeastOnce()).createStep(anyLong(), anyInt(), anyString(), anyString(), anyString(), anyLong(), anyString());
        verify(persistenceService).updateTaskSuccess(anyLong(), anyString(), anyString(), anyLong());
    }

    @Test
    void shouldUseSummaryToolWhenSummaryIntentAndParseSuccess() {
        DocumentAgentServiceImpl service = buildService();

        DocumentAgentRequest request = new DocumentAgentRequest();
        request.setDocumentId(101L);
        request.setTask("Please summarize this document for interview showcase");

        when(documentStatusTool.execute(new DocumentStatusTool.StatusInput(100L, 101L)))
                .thenReturn(new DocumentStatusTool.StatusResult(
                        101L,
                        "demo",
                        ParseStatusConstants.SUCCESS,
                        true,
                        "ready",
                        "This is the summary field.",
                        "This is the full content."
                ));
        when(documentStatusTool.getToolName()).thenReturn("document_status_tool");
        when(documentSummaryTool.getToolName()).thenReturn("document_summary_tool");
        when(documentSummaryTool.execute(new DocumentSummaryTool.SummaryInput(
                "Please summarize this document for interview showcase",
                "This is the summary field.",
                "This is the full content."
        ))).thenReturn(new DocumentSummaryTool.SummaryResult("This is the summary field.", "summary_field"));
        stubPersistenceTask();

        var response = service.run(100L, request);

        assertEquals(1001L, response.getTaskId());
        assertEquals("summary_tool", response.getDecision());
        assertEquals("This is the summary field.", response.getFinalAnswer());
        assertEquals(2, response.getSteps().size());
        assertTrue(response.isSuccess());
        assertNotNull(response.getTraceId());
        assertNotNull(response.getStartedAt());
        assertNotNull(response.getFinishedAt());
        assertTrue(response.getTotalDurationMs() >= 0);
        verifyPersistenceSuccess();
        verify(documentQaTool, never()).execute(any());
    }

    @Test
    void shouldUseQaToolWhenTaskNeedsEvidence() {
        DocumentAgentServiceImpl service = buildService();

        DocumentAgentRequest request = new DocumentAgentRequest();
        request.setDocumentId(102L);
        request.setTask("Please answer with evidence and cite the key points.");
        request.setSessionId("sess-qa");

        when(documentStatusTool.execute(new DocumentStatusTool.StatusInput(100L, 102L)))
                .thenReturn(new DocumentStatusTool.StatusResult(
                        102L,
                        "demo",
                        ParseStatusConstants.SUCCESS,
                        true,
                        "ready",
                        "summary",
                        "content"
                ));
        when(documentStatusTool.getToolName()).thenReturn("document_status_tool");
        when(documentQaTool.getToolName()).thenReturn("document_qa_tool");

        DocumentQaResponse qaResponse = new DocumentQaResponse();
        qaResponse.setDocumentId(102L);
        qaResponse.setSessionId("sess-qa");
        qaResponse.setAnswer("This is the answer backed by document evidence.");
        qaResponse.setCitations(List.of(new DocumentQaResponse.CitationItem()));
        when(documentQaTool.execute(new DocumentQaTool.QaInput(100L, 102L, "Please answer with evidence and cite the key points.", "sess-qa")))
                .thenReturn(qaResponse);
        stubPersistenceTask();

        var response = service.run(100L, request);

        assertEquals(1001L, response.getTaskId());
        assertEquals("qa_tool", response.getDecision());
        assertEquals("This is the answer backed by document evidence.", response.getFinalAnswer());
        assertEquals("sess-qa", response.getSessionId());
        assertNotNull(response.getCitations());
        assertFalse(response.getCitations().isEmpty());
        assertEquals(2, response.getSteps().size());
        assertTrue(response.isSuccess());
        verifyPersistenceSuccess();
    }

    @Test
    void shouldReturnPendingHintWhenParseNotReady() {
        DocumentAgentServiceImpl service = buildService();

        DocumentAgentRequest request = new DocumentAgentRequest();
        request.setDocumentId(103L);
        request.setTask("Please summarize this document");

        when(documentStatusTool.execute(new DocumentStatusTool.StatusInput(100L, 103L)))
                .thenReturn(new DocumentStatusTool.StatusResult(
                        103L,
                        "demo",
                        ParseStatusConstants.PARSING,
                        false,
                        "parsing now",
                        null,
                        null
                ));
        when(documentStatusTool.getToolName()).thenReturn("document_status_tool");
        stubPersistenceTask();

        var response = service.run(100L, request);

        assertEquals(1001L, response.getTaskId());
        assertEquals("status_only", response.getDecision());
        assertNull(response.getSessionId());
        assertTrue(response.getFinalAnswer().contains("PARSING"));
        verifyPersistenceSuccess();
        verify(documentSummaryTool, never()).execute(any());
        verify(documentQaTool, never()).execute(any());
    }
}
