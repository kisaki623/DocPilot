package com.docpilot.backend.memory.eval;

import com.docpilot.backend.ai.context.ContextAssemblyRequest;
import com.docpilot.backend.ai.context.ContextAssemblyResult;
import com.docpilot.backend.ai.context.ContextItem;
import com.docpilot.backend.ai.context.ContextPolicy;
import com.docpilot.backend.ai.context.ContextTrace;
import com.docpilot.backend.ai.context.ContextType;
import com.docpilot.backend.ai.context.builder.KnowledgeBaseEvidenceBuilder;
import com.docpilot.backend.ai.context.builder.KnowledgeBaseEvidenceResult;
import com.docpilot.backend.ai.context.builder.RecentTurnsContextBuilder;
import com.docpilot.backend.ai.context.impl.ContextAssemblyServiceImpl;
import com.docpilot.backend.ai.context.memory.MemorySelector;
import com.docpilot.backend.ai.context.render.PromptRenderer;
import com.docpilot.backend.ai.context.security.ContextPermissionFilter;
import com.docpilot.backend.ai.context.token.TokenBudgetManager;
import com.docpilot.backend.ai.context.token.TokenEstimator;
import com.docpilot.backend.common.exception.BusinessException;
import com.docpilot.backend.conversation.constant.ConversationMessageStatus;
import com.docpilot.backend.conversation.entity.Conversation;
import com.docpilot.backend.conversation.entity.ConversationMessage;
import com.docpilot.backend.conversation.entity.ConversationSummary;
import com.docpilot.backend.conversation.mapper.ConversationMessageMapper;
import com.docpilot.backend.conversation.service.ConversationService;
import com.docpilot.backend.conversation.service.ConversationSummaryService;
import com.docpilot.backend.memory.constant.UserMemorySourceType;
import com.docpilot.backend.memory.entity.UserMemory;
import com.docpilot.backend.memory.mapper.UserMemoryMapper;
import com.docpilot.backend.memory.service.MemorySafetyValidator;
import com.docpilot.backend.memory.service.MemorySuggestionCandidate;
import com.docpilot.backend.memory.service.impl.RuleBasedMemoryExtractionService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MemoryQualityEvalRunner {

    public static final String CASES_RESOURCE = "/memory/memory-quality-eval-cases.json";
    public static final Path DEFAULT_REPORT_PATH = Path.of("target", "memory-eval", "memory-quality-eval-latest.json");

    private final ObjectMapper objectMapper;

    public MemoryQualityEvalRunner() {
        this(new ObjectMapper());
    }

    public MemoryQualityEvalRunner(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
    }

    public List<MemoryQualityEvalCase> loadCases() throws IOException {
        try (InputStream inputStream = getClass().getResourceAsStream(CASES_RESOURCE)) {
            if (inputStream == null) {
                throw new IllegalStateException("memory quality eval cases resource is missing");
            }
            return objectMapper.readValue(inputStream, new TypeReference<>() {
            });
        }
    }

    public MemoryQualityEvalResult evaluateDefaultCases() throws IOException {
        return evaluate(loadCases());
    }

    public MemoryQualityEvalResult evaluate(List<MemoryQualityEvalCase> cases) {
        List<MemoryQualityEvalResult.CaseEvaluation> evaluations = (cases == null ? List.<MemoryQualityEvalCase>of() : cases)
                .stream()
                .map(this::evaluateOne)
                .toList();
        return new MemoryQualityEvalResult(MemoryQualityEvalMetrics.from(evaluations), evaluations);
    }

    public void writeArtifact(MemoryQualityEvalResult result, Path path) throws IOException {
        Path resolvedPath = path == null ? DEFAULT_REPORT_PATH : path;
        Files.createDirectories(resolvedPath.getParent());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(resolvedPath.toFile(), result.toSafeMap());
    }

    private MemoryQualityEvalResult.CaseEvaluation evaluateOne(MemoryQualityEvalCase evalCase) {
        EvalHarness harness = new EvalHarness(evalCase);
        List<String> failureReasons = new ArrayList<>();

        List<MemorySuggestionCandidate> suggestions = harness.extractionService.extractSuggestions(
                evalCase.userId(), evalCase.conversationId(), null);
        List<String> suggestionTypes = suggestions.stream().map(MemorySuggestionCandidate::memoryType).toList();
        boolean suggestionTypesHit = suggestionTypes.equals(evalCase.expectedSuggestionTypes());
        if (!suggestionTypesHit) {
            failureReasons.add("suggestion_types_mismatch");
        }

        boolean ragEvidenceIsolationHit = evalCase.forbiddenSuggestionMarkers().stream()
                .noneMatch(marker -> suggestions.stream().anyMatch(candidate -> candidate.content().contains(marker)));
        if (!ragEvidenceIsolationHit) {
            failureReasons.add("rag_evidence_leaked_to_memory_suggestion");
        }

        boolean sensitiveRejected = false;
        if (!evalCase.sensitiveProbe().isBlank()) {
            try {
                harness.safetyValidator.validate(evalCase.sensitiveProbe());
            } catch (BusinessException ex) {
                sensitiveRejected = true;
            }
        }
        if (evalCase.expectSensitiveRejected() && !sensitiveRejected) {
            failureReasons.add("sensitive_probe_not_rejected");
        }

        boolean suggestionSafetyHit = suggestions.stream().allMatch(candidate -> {
            try {
                harness.safetyValidator.validate(candidate.content());
                return true;
            } catch (BusinessException ex) {
                return false;
            }
        });
        if (!suggestionSafetyHit) {
            failureReasons.add("unsafe_memory_suggestion_extracted");
        }

        ContextAssemblyResult context = harness.contextAssemblyService.buildContext(new ContextAssemblyRequest(
                evalCase.userId(), evalCase.conversationId(), evalCase.currentMessage(), null));
        List<Long> selectedMemoryIds = context.usedItems().stream()
                .filter(item -> item.type() == ContextType.MEMORY)
                .map(ContextItem::sourceId)
                .map(Long::valueOf)
                .toList();
        boolean selectedExpected = selectedMemoryIds.equals(evalCase.expectedSelectedMemoryIds());
        boolean forbiddenMissing = selectedMemoryIds.stream().noneMatch(evalCase.forbiddenSelectedMemoryIds()::contains);
        boolean activeMemorySelectionHit = selectedExpected && forbiddenMissing;
        if (!activeMemorySelectionHit) {
            failureReasons.add("active_memory_selection_mismatch");
        }

        ContextTrace trace = context.trace();
        Map<String, Integer> contextSourceCounts = trace.getContextSourceCounts();
        boolean traceCountsHit = evalCase.expectedTraceCounts().entrySet().stream()
                .allMatch(entry -> contextSourceCounts.getOrDefault(entry.getKey(), -1).equals(entry.getValue()));
        if (!traceCountsHit) {
            failureReasons.add("trace_source_counts_mismatch");
        }

        boolean passed = suggestionTypesHit
                && activeMemorySelectionHit
                && (!evalCase.expectSensitiveRejected() || sensitiveRejected)
                && suggestionSafetyHit
                && ragEvidenceIsolationHit
                && traceCountsHit;

        return new MemoryQualityEvalResult.CaseEvaluation(
                evalCase.id(),
                evalCase.category(),
                suggestions.size(),
                suggestionTypes,
                selectedMemoryIds,
                contextSourceCounts,
                suggestionTypesHit,
                activeMemorySelectionHit,
                evalCase.expectSensitiveRejected(),
                sensitiveRejected,
                suggestionSafetyHit,
                ragEvidenceIsolationHit,
                traceCountsHit,
                passed,
                failureReasons,
                "rule_based",
                false
        );
    }

    private static final class EvalHarness {

        private final RuleBasedMemoryExtractionService extractionService;
        private final MemorySafetyValidator safetyValidator = new MemorySafetyValidator();
        private final ContextAssemblyServiceImpl contextAssemblyService;

        private EvalHarness(MemoryQualityEvalCase evalCase) {
            ConversationService conversationService = mock(ConversationService.class);
            ConversationSummaryService summaryService = mock(ConversationSummaryService.class);
            ConversationMessageMapper messageMapper = mock(ConversationMessageMapper.class);
            UserMemoryMapper memoryMapper = mock(UserMemoryMapper.class);
            KnowledgeBaseEvidenceBuilder evidenceBuilder = mock(KnowledgeBaseEvidenceBuilder.class);
            TokenEstimator tokenEstimator = new TokenEstimator();

            Conversation conversation = conversation(evalCase);
            when(conversationService.requireOwnedActive(evalCase.userId(), evalCase.conversationId())).thenReturn(conversation);
            when(messageMapper.selectRecentActive(eq(evalCase.userId()), eq(evalCase.conversationId()), anyInt()))
                    .thenReturn(messages(evalCase));
            when(memoryMapper.selectActiveByUser(eq(evalCase.userId()), eq(null), anyInt()))
                    .thenReturn(memories(evalCase));
            when(summaryService.getActiveSummary(evalCase.userId(), evalCase.conversationId()))
                    .thenReturn(summary(evalCase));
            when(evidenceBuilder.build(eq(conversation), eq(evalCase.currentMessage()), any(ContextPolicy.class)))
                    .thenReturn(evidence(evalCase, tokenEstimator));

            this.extractionService = new RuleBasedMemoryExtractionService(conversationService, messageMapper);
            MemorySelector memorySelector = new MemorySelector(memoryMapper, tokenEstimator);
            this.contextAssemblyService = new ContextAssemblyServiceImpl(
                    conversationService,
                    summaryService,
                    new RecentTurnsContextBuilder(messageMapper, tokenEstimator),
                    memorySelector,
                    evidenceBuilder,
                    new ContextPermissionFilter(),
                    new TokenBudgetManager(),
                    new PromptRenderer(),
                    tokenEstimator
            );
        }

        private Conversation conversation(MemoryQualityEvalCase evalCase) {
            Conversation conversation = new Conversation();
            conversation.setId(evalCase.conversationId());
            conversation.setUserId(evalCase.userId());
            conversation.setContextMode(evalCase.contextMode());
            conversation.setBoundKnowledgeBaseId(evalCase.boundKnowledgeBaseId());
            conversation.setSummaryEnabled(evalCase.summaryEnabled());
            conversation.setMemoryEnabled(evalCase.memoryEnabled());
            conversation.setStatus("ACTIVE");
            return conversation;
        }

        private ConversationSummary summary(MemoryQualityEvalCase evalCase) {
            if (!evalCase.summaryEnabled() || evalCase.summaryText().isBlank()) {
                return null;
            }
            ConversationSummary summary = new ConversationSummary();
            summary.setId(evalCase.conversationId() * 10);
            summary.setConversationId(evalCase.conversationId());
            summary.setUserId(evalCase.userId());
            summary.setSummary(evalCase.summaryText());
            summary.setSummaryVersion(1);
            summary.setStatus("ACTIVE");
            return summary;
        }

        private List<ConversationMessage> messages(MemoryQualityEvalCase evalCase) {
            return evalCase.messages().stream()
                    .sorted(Comparator.comparingInt(message -> message.sequenceNo() == null ? 0 : message.sequenceNo()))
                    .map(message -> toMessage(evalCase, message))
                    .toList();
        }

        private ConversationMessage toMessage(MemoryQualityEvalCase evalCase, MemoryQualityEvalCase.EvalMessage input) {
            ConversationMessage message = new ConversationMessage();
            message.setId(input.id());
            message.setConversationId(evalCase.conversationId());
            message.setUserId(evalCase.userId());
            message.setRole(input.role());
            message.setContent(input.content());
            message.setSequenceNo(input.sequenceNo());
            message.setStatus(ConversationMessageStatus.ACTIVE);
            return message;
        }

        private List<UserMemory> memories(MemoryQualityEvalCase evalCase) {
            return evalCase.memories().stream()
                    .map(memory -> toMemory(evalCase, memory))
                    .toList();
        }

        private UserMemory toMemory(MemoryQualityEvalCase evalCase, MemoryQualityEvalCase.EvalMemory input) {
            UserMemory memory = new UserMemory();
            memory.setId(input.id());
            memory.setUserId(evalCase.userId());
            memory.setMemoryType(input.memoryType());
            memory.setContent(input.content());
            memory.setSourceType(UserMemorySourceType.SYSTEM_EXTRACTED);
            memory.setSourceConversationId(evalCase.conversationId());
            memory.setSourceMessageId(input.id());
            memory.setStatus(input.status());
            memory.setPriority(input.priority());
            memory.setConfidence(BigDecimal.valueOf(0.7));
            return memory;
        }

        private KnowledgeBaseEvidenceResult evidence(MemoryQualityEvalCase evalCase, TokenEstimator tokenEstimator) {
            if (evalCase.ragEvidenceCount() <= 0) {
                return KnowledgeBaseEvidenceResult.notTriggered();
            }
            List<ContextItem> items = new ArrayList<>();
            for (int i = 0; i < evalCase.ragEvidenceCount(); i++) {
                String content = "redacted evidence " + (i + 1);
                items.add(new ContextItem(
                        ContextType.RAG_EVIDENCE,
                        content,
                        900 - i,
                        tokenEstimator.estimate(content),
                        false,
                        evalCase.userId(),
                        "evidence-" + (i + 1),
                        "ACTIVE",
                        Map.of("redacted", true)
                ));
            }
            return new KnowledgeBaseEvidenceResult(
                    true,
                    evalCase.ragRequired(),
                    false,
                    "",
                    items,
                    List.of(),
                    new LinkedHashMap<>(evalCase.documentHitCounts())
            );
        }
    }
}
