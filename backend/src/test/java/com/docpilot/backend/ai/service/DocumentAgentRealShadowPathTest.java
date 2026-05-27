package com.docpilot.backend.ai.service;

import com.docpilot.backend.ai.agent.config.AgentSelectorProperties;
import com.docpilot.backend.ai.agent.dto.DocumentAgentRequest;
import com.docpilot.backend.ai.agent.entity.AgentTask;
import com.docpilot.backend.ai.agent.service.AgentTaskPersistenceService;
import com.docpilot.backend.ai.agent.service.impl.DocumentAgentServiceImpl;
import com.docpilot.backend.ai.agent.tool.DocumentQaTool;
import com.docpilot.backend.ai.agent.tool.DocumentStatusTool;
import com.docpilot.backend.ai.agent.tool.DocumentSummaryTool;
import com.docpilot.backend.ai.agent.tool.LlmToolSelectionParser;
import com.docpilot.backend.ai.agent.tool.LlmToolSelectionPromptBuilder;
import com.docpilot.backend.ai.agent.tool.LlmToolSelectionResult;
import com.docpilot.backend.ai.agent.tool.LlmToolSelector;
import com.docpilot.backend.ai.agent.tool.SelectorMetricsCollector;
import com.docpilot.backend.ai.agent.tool.SelectorMetricsSnapshot;
import com.docpilot.backend.ai.agent.tool.ToolDefinition;
import com.docpilot.backend.ai.agent.tool.ToolDefinitionProvider;
import com.docpilot.backend.ai.agent.tool.ToolRegistry;
import com.docpilot.backend.ai.agent.tool.ToolSelector;
import com.docpilot.backend.common.constant.ParseStatusConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentAgentRealShadowPathTest {

    @Mock
    private DocumentStatusTool documentStatusTool;

    @Mock
    private DocumentSummaryTool documentSummaryTool;

    @Mock
    private DocumentQaTool documentQaTool;

    @Mock
    private ToolRegistry toolRegistry;

    @Mock
    private ToolSelector toolSelector;

    @Mock
    private AgentTaskPersistenceService persistenceService;

    @Mock
    private LlmToolSelector fakeShadowSelector;

    @Mock
    private ToolDefinitionProvider toolDefinitionProvider;

    @Mock
    private LlmToolSelectionPromptBuilder realShadowPromptBuilder;

    @Mock
    private LlmToolSelectionParser realShadowParser;

    private SelectorMetricsCollector metricsCollector;

    @Test
    void shouldSkipRealShadowByDefault() {
        AgentSelectorProperties properties = new AgentSelectorProperties();
        DocumentAgentServiceImpl service = buildService(properties);
        stubReadySummaryFlow(201L);

        var response = service.run(100L, request(201L));

        assertEquals("summary_tool", response.getDecision());
        verify(realShadowPromptBuilder, never()).build(anyString(), anyBoolean(), anyBoolean(), anyList());
        assertMetrics(0L, 0L, 0L);
    }

    @Test
    void shouldNotEnableRealShadowWhenOnlyFakeShadowIsEnabled() {
        AgentSelectorProperties properties = new AgentSelectorProperties();
        properties.setShadowEnabled(true);
        DocumentAgentServiceImpl service = buildService(properties);
        stubReadySummaryFlow(202L);
        stubFakeShadowSummary();

        var response = service.run(100L, request(202L));

        assertEquals("summary_tool", response.getDecision());
        verify(fakeShadowSelector).selectWithPrompt(anyString(), anyBoolean(), anyBoolean(), anyList());
        verify(realShadowPromptBuilder, never()).build(anyString(), anyBoolean(), anyBoolean(), anyList());
        assertMetrics(1L, 1L, 0L);
    }

    @Test
    void shouldFailOpenWhenRealShadowDisabledClientRuns() {
        AgentSelectorProperties properties = realShadowEnabledProperties(false);
        DocumentAgentServiceImpl service = buildService(properties);
        stubReadySummaryFlow(203L);
        stubFakeShadowSummary();
        when(realShadowPromptBuilder.build(anyString(), anyBoolean(), anyBoolean(), anyList()))
                .thenReturn("prompt");

        var response = service.run(100L, request(203L));

        assertEquals("summary_tool", response.getDecision());
        verify(realShadowPromptBuilder).build(anyString(), anyBoolean(), anyBoolean(), anyList());
        verify(realShadowParser, never()).parse(anyString());
        assertMetrics(1L, 1L, 0L);
    }

    @Test
    void shouldFailOpenWhenRealShadowThrows() {
        AgentSelectorProperties properties = realShadowEnabledProperties(false);
        DocumentAgentServiceImpl service = buildService(properties);
        stubReadySummaryFlow(204L);
        stubFakeShadowSummary();
        when(realShadowPromptBuilder.build(anyString(), anyBoolean(), anyBoolean(), anyList()))
                .thenThrow(new IllegalStateException("real shadow unavailable"));

        var response = service.run(100L, request(204L));

        assertEquals("summary_tool", response.getDecision());
        verify(realShadowParser, never()).parse(anyString());
        assertMetrics(1L, 1L, 0L);
    }

    @Test
    void shouldFailOpenWhenOpenAiCompatibleApiKeyBlank() {
        AgentSelectorProperties properties = realShadowEnabledProperties(false, "openai_compatible");
        properties.setLlmModel("selector-model");
        properties.setLlmBaseUrl("https://example.invalid/v1");
        DocumentAgentServiceImpl service = buildService(properties);
        stubReadySummaryFlow(214L);
        stubFakeShadowSummary();
        when(realShadowPromptBuilder.build(anyString(), anyBoolean(), anyBoolean(), anyList()))
                .thenReturn("prompt");

        var response = service.run(100L, request(214L));

        assertEquals("summary_tool", response.getDecision());
        verify(realShadowParser, never()).parse(anyString());
        assertMetrics(1L, 1L, 0L);
    }

    @Test
    void shouldFailOpenWhenOpenAiCompatibleBaseUrlBlank() {
        AgentSelectorProperties properties = realShadowEnabledProperties(false, "openai_compatible");
        properties.setLlmModel("selector-model");
        properties.setLlmApiKey("test-key-not-used");
        DocumentAgentServiceImpl service = buildService(properties);
        stubReadySummaryFlow(215L);
        stubFakeShadowSummary();
        when(realShadowPromptBuilder.build(anyString(), anyBoolean(), anyBoolean(), anyList()))
                .thenReturn("prompt");

        var response = service.run(100L, request(215L));

        assertEquals("summary_tool", response.getDecision());
        verify(realShadowParser, never()).parse(anyString());
        assertMetrics(1L, 1L, 0L);
    }

    @Test
    void shouldFailOpenWhenRealShadowParserFails() {
        AgentSelectorProperties properties = realShadowEnabledProperties(true, "fake");
        DocumentAgentServiceImpl service = buildService(properties);
        stubReadySummaryFlow(216L);
        stubFakeShadowSummary();
        when(realShadowPromptBuilder.build(anyString(), anyBoolean(), anyBoolean(), anyList()))
                .thenReturn("Current task: Please summarize this document");
        when(realShadowParser.parse(anyString())).thenThrow(new IllegalArgumentException("bad shadow json"));

        var response = service.run(100L, request(216L));

        assertEquals("summary_tool", response.getDecision());
        verify(realShadowParser).parse(anyString());
        assertMetrics(1L, 1L, 0L);
    }

    @Test
    void shouldSkipRealShadowWhenParseNotReady() {
        AgentSelectorProperties properties = realShadowEnabledProperties(true);
        DocumentAgentServiceImpl service = buildService(properties);
        stubParseNotReady(205L);

        var response = service.run(100L, request(205L));

        assertEquals("status_only", response.getDecision());
        verify(fakeShadowSelector, never()).selectWithPrompt(anyString(), anyBoolean(), anyBoolean(), anyList());
        verify(realShadowPromptBuilder, never()).build(anyString(), anyBoolean(), anyBoolean(), anyList());
        assertMetrics(0L, 0L, 0L);
    }

    @Test
    void shouldNotRecordRealMetricsWhenRealShadowRecordMetricsDisabled() {
        AgentSelectorProperties properties = realShadowEnabledProperties(false, "fake");
        DocumentAgentServiceImpl service = buildService(properties);
        stubReadySummaryFlow(206L);
        stubFakeShadowSummary();
        stubSuccessfulRealShadow("qa_tool");

        var response = service.run(100L, request(206L));

        assertEquals("summary_tool", response.getDecision());
        verify(realShadowParser).parse(anyString());
        assertMetrics(1L, 1L, 0L);
    }

    @Test
    void shouldRecordRealMetricsWhenFlagEnabledAndRealShadowSucceeds() {
        AgentSelectorProperties properties = realShadowEnabledProperties(true, "fake");
        DocumentAgentServiceImpl service = buildService(properties);
        stubReadySummaryFlow(207L);
        stubFakeShadowSummary();
        stubSuccessfulRealShadow("qa_tool");

        var response = service.run(100L, request(207L));

        assertEquals("summary_tool", response.getDecision());
        verify(realShadowParser).parse(anyString());
        assertMetrics(2L, 1L, 1L);
    }

    private DocumentAgentServiceImpl buildService(AgentSelectorProperties properties) {
        metricsCollector = new SelectorMetricsCollector();
        return new DocumentAgentServiceImpl(
                toolRegistry,
                toolSelector,
                persistenceService,
                properties,
                fakeShadowSelector,
                toolDefinitionProvider,
                metricsCollector,
                realShadowPromptBuilder,
                realShadowParser
        );
    }

    private DocumentAgentRequest request(Long documentId) {
        DocumentAgentRequest request = new DocumentAgentRequest();
        request.setDocumentId(documentId);
        request.setTask("Please summarize this document");
        return request;
    }

    private AgentSelectorProperties realShadowEnabledProperties(boolean recordMetrics) {
        return realShadowEnabledProperties(recordMetrics, "disabled");
    }

    private AgentSelectorProperties realShadowEnabledProperties(boolean recordMetrics, String provider) {
        AgentSelectorProperties properties = new AgentSelectorProperties();
        properties.setShadowEnabled(true);
        properties.setRealShadowEnabled(true);
        properties.setRealShadowRecordMetrics(recordMetrics);
        properties.setLlmProvider(provider);
        return properties;
    }

    private void stubReadySummaryFlow(Long documentId) {
        when(documentStatusTool.execute(new DocumentStatusTool.StatusInput(100L, documentId)))
                .thenReturn(new DocumentStatusTool.StatusResult(
                        documentId,
                        "demo",
                        ParseStatusConstants.SUCCESS,
                        true,
                        "ready",
                        "summary",
                        "content"
                ));
        when(documentStatusTool.getToolName()).thenReturn("document_status_tool");
        when(documentSummaryTool.getToolName()).thenReturn("document_summary_tool");
        when(toolSelector.select(anyString())).thenReturn(new ToolSelector.SelectResult(
                "summary_tool",
                List.of("document_status_tool", "document_summary_tool"),
                "summary reason",
                List.of("summary")
        ));
        when(documentSummaryTool.execute(new DocumentSummaryTool.SummaryInput(
                "Please summarize this document",
                "summary",
                "content"
        ))).thenReturn(new DocumentSummaryTool.SummaryResult("summary", "summary_field"));
        stubStatusTool();
        stubSummaryTool();
        stubPersistenceTask();
    }

    private void stubParseNotReady(Long documentId) {
        when(documentStatusTool.execute(new DocumentStatusTool.StatusInput(100L, documentId)))
                .thenReturn(new DocumentStatusTool.StatusResult(
                        documentId,
                        "demo",
                        ParseStatusConstants.PARSING,
                        false,
                        "parsing now",
                        null,
                        null
                ));
        when(documentStatusTool.getToolName()).thenReturn("document_status_tool");
        stubStatusTool();
        stubPersistenceTask();
    }

    private void stubFakeShadowSummary() {
        when(toolDefinitionProvider.getAllDefinitions()).thenReturn(toolDefinitions());
        when(fakeShadowSelector.selectWithPrompt(anyString(), anyBoolean(), anyBoolean(), anyList()))
                .thenReturn(new LlmToolSelectionResult(
                        "summary_tool",
                        List.of("document_status_tool", "document_summary_tool"),
                        "fake shadow reason",
                        List.of("summary"),
                        0.9d
                ));
    }

    private void stubSuccessfulRealShadow(String decision) {
        when(realShadowPromptBuilder.build(anyString(), anyBoolean(), anyBoolean(), anyList()))
                .thenReturn("Current task: Please summarize this document");
        when(realShadowParser.parse(anyString())).thenReturn(new LlmToolSelectionResult(
                decision,
                List.of("document_status_tool", "document_qa_tool"),
                "real shadow reason",
                List.of("document"),
                0.8d
        ));
    }

    private List<ToolDefinition> toolDefinitions() {
        return List.of(
                new ToolDefinition("document_status_tool", "Document status", "Checks parse status.", "{}", "{}", true),
                new ToolDefinition("document_summary_tool", "Document summary", "Returns summary.", "{}", "{}", true),
                new ToolDefinition("document_qa_tool", "Document QA", "Answers with citations.", "{}", "{}", true)
        );
    }

    private void stubStatusTool() {
        when(toolRegistry.<DocumentStatusTool>get("document_status_tool")).thenReturn(documentStatusTool);
    }

    private void stubSummaryTool() {
        when(toolRegistry.<DocumentSummaryTool>get("document_summary_tool")).thenReturn(documentSummaryTool);
    }

    private void stubPersistenceTask() {
        AgentTask mockTask = new AgentTask();
        mockTask.setId(2001L);
        when(persistenceService.createTask(anyLong(), anyLong(), anyString(), any())).thenReturn(mockTask);
    }

    private void assertMetrics(long total, long matched, long mismatch) {
        SelectorMetricsSnapshot snapshot = metricsCollector.snapshot();
        assertEquals(total, snapshot.totalComparisons());
        assertEquals(matched, snapshot.matchedCount());
        assertEquals(mismatch, snapshot.mismatchCount());
    }
}
