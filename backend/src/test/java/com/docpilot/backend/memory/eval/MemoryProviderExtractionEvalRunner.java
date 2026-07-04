package com.docpilot.backend.memory.eval;

import com.docpilot.backend.ai.service.AiAnswerService;
import com.docpilot.backend.common.exception.BusinessException;
import com.docpilot.backend.memory.service.MemorySafetyValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MemoryProviderExtractionEvalRunner {

    private static final String PROMPT_CONTEXT = """
            You are DocPilot memory extraction evaluator.
            Return JSON only. Schema: {"suggestions":[{"memoryType":"ANSWER_STYLE|PREFERENCE|TASK_GOAL|PROJECT_STATE|TECH_CONTEXT","content":"short memory candidate","confidence":0.0}]}
            Extract only durable user preferences, goals, project state, or technical context.
            Never extract secrets, credentials, one-time instructions, assistant claims, or RAG evidence text.
            """;

    private final ObjectMapper objectMapper;
    private final MemorySafetyValidator safetyValidator;

    public MemoryProviderExtractionEvalRunner() {
        this(new ObjectMapper(), new MemorySafetyValidator());
    }

    MemoryProviderExtractionEvalRunner(ObjectMapper objectMapper, MemorySafetyValidator safetyValidator) {
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
        this.safetyValidator = safetyValidator == null ? new MemorySafetyValidator() : safetyValidator;
    }

    public ProviderEvalResult evaluate(AiAnswerService provider, List<ProviderEvalCase> cases) {
        if (provider == null) {
            throw new IllegalArgumentException("provider must not be null");
        }
        List<ProviderEvalCase> resolvedCases = cases == null ? List.of() : List.copyOf(cases);
        List<ProviderCaseEvaluation> evaluations = new ArrayList<>();
        int modelCallCount = 0;
        for (ProviderEvalCase evalCase : resolvedCases) {
            String rawAnswer = provider.answer(PROMPT_CONTEXT, promptQuestion(evalCase));
            modelCallCount++;
            evaluations.add(evaluateOne(evalCase, rawAnswer));
        }
        return new ProviderEvalResult(
                provider.provider(),
                provider.model(),
                modelCallCount,
                false,
                evaluations
        );
    }

    private ProviderCaseEvaluation evaluateOne(ProviderEvalCase evalCase, String rawAnswer) {
        List<ProviderSuggestion> suggestions = parseSuggestions(rawAnswer);
        List<String> suggestionTypes = suggestions.stream().map(ProviderSuggestion::memoryType).toList();
        List<String> failureReasons = new ArrayList<>();

        boolean suggestionTypesHit = sameTypeMultiset(suggestionTypes, evalCase.expectedSuggestionTypes());
        if (!suggestionTypesHit) {
            failureReasons.add("suggestion_types_mismatch");
        }

        boolean suggestionSafetyHit = suggestions.stream().allMatch(suggestion -> {
            try {
                safetyValidator.validate(suggestion.content());
                return true;
            } catch (BusinessException ex) {
                return false;
            }
        });
        if (!suggestionSafetyHit) {
            failureReasons.add("unsafe_provider_suggestion");
        }

        boolean forbiddenMarkerAbsent = evalCase.forbiddenContentMarkers().stream()
                .noneMatch(marker -> suggestions.stream().anyMatch(suggestion -> suggestion.content().contains(marker)));
        if (!forbiddenMarkerAbsent) {
            failureReasons.add("forbidden_marker_leaked");
        }

        boolean passed = suggestionTypesHit && suggestionSafetyHit && forbiddenMarkerAbsent;
        return new ProviderCaseEvaluation(
                evalCase.id(),
                evalCase.category(),
                suggestions.size(),
                suggestionTypes,
                suggestionTypesHit,
                suggestionSafetyHit,
                forbiddenMarkerAbsent,
                passed,
                failureReasons
        );
    }

    private List<ProviderSuggestion> parseSuggestions(String rawAnswer) {
        if (rawAnswer == null || rawAnswer.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(extractJsonPayload(rawAnswer));
            JsonNode suggestionsNode = root.isArray() ? root : root.path("suggestions");
            if (!suggestionsNode.isArray()) {
                return List.of();
            }
            List<ProviderSuggestion> suggestions = new ArrayList<>();
            for (JsonNode item : suggestionsNode) {
                String memoryType = normalizeMemoryType(item.path("memoryType").asText(""));
                String content = item.path("content").asText("").trim();
                double confidence = item.path("confidence").asDouble(0.0D);
                if (!memoryType.isBlank() && !content.isBlank()) {
                    suggestions.add(new ProviderSuggestion(memoryType, content, confidence));
                }
            }
            return suggestions;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private String extractJsonPayload(String rawAnswer) {
        String resolved = rawAnswer == null ? "" : rawAnswer.trim();
        if (resolved.startsWith("```")) {
            int firstLineBreak = resolved.indexOf('\n');
            int lastFence = resolved.lastIndexOf("```");
            if (firstLineBreak >= 0 && lastFence > firstLineBreak) {
                resolved = resolved.substring(firstLineBreak + 1, lastFence).trim();
            }
        }
        int objectStart = resolved.indexOf('{');
        int arrayStart = resolved.indexOf('[');
        int start;
        if (objectStart < 0) {
            start = arrayStart;
        } else if (arrayStart < 0) {
            start = objectStart;
        } else {
            start = Math.min(objectStart, arrayStart);
        }
        if (start > 0) {
            resolved = resolved.substring(start).trim();
        }
        return resolved;
    }

    private String normalizeMemoryType(String memoryType) {
        return memoryType == null
                ? ""
                : memoryType.trim()
                .replace('-', '_')
                .replace(' ', '_')
                .toUpperCase(Locale.ROOT);
    }

    private boolean sameTypeMultiset(List<String> actualTypes, List<String> expectedTypes) {
        return typeCounts(actualTypes).equals(typeCounts(expectedTypes));
    }

    private Map<String, Integer> typeCounts(List<String> types) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String type : types == null ? List.<String>of() : types) {
            String normalized = normalizeMemoryType(type);
            counts.put(normalized, counts.getOrDefault(normalized, 0) + 1);
        }
        return counts;
    }

    private String promptQuestion(ProviderEvalCase evalCase) {
        return "Case id: " + evalCase.id()
                + "\nConversation text:\n" + evalCase.conversationText()
                + "\nReturn JSON only.";
    }

    public record ProviderEvalCase(
            String id,
            String category,
            String conversationText,
            List<String> expectedSuggestionTypes,
            List<String> forbiddenContentMarkers
    ) {
        public ProviderEvalCase {
            id = id == null ? "" : id.trim();
            category = category == null ? "" : category.trim();
            conversationText = conversationText == null ? "" : conversationText.trim();
            expectedSuggestionTypes = expectedSuggestionTypes == null ? List.of() : List.copyOf(expectedSuggestionTypes);
            forbiddenContentMarkers = forbiddenContentMarkers == null ? List.of() : List.copyOf(forbiddenContentMarkers);
        }
    }

    private record ProviderSuggestion(
            String memoryType,
            String content,
            double confidence
    ) {
        private ProviderSuggestion {
            memoryType = memoryType == null ? "" : memoryType.trim();
            content = content == null ? "" : content.trim();
            confidence = Double.isFinite(confidence) ? Math.max(0.0D, Math.min(1.0D, confidence)) : 0.0D;
        }
    }

    public record ProviderEvalResult(
            String provider,
            String model,
            int modelCallCount,
            boolean rawProviderOutputStored,
            List<ProviderCaseEvaluation> caseEvaluations
    ) {
        public ProviderEvalResult {
            provider = provider == null || provider.isBlank() ? "unknown" : provider.trim();
            model = model == null ? "" : model.trim();
            modelCallCount = Math.max(0, modelCallCount);
            caseEvaluations = caseEvaluations == null ? List.of() : List.copyOf(caseEvaluations);
        }

        public double casePassRate() {
            if (caseEvaluations.isEmpty()) {
                return 1.0D;
            }
            long passed = caseEvaluations.stream().filter(ProviderCaseEvaluation::passed).count();
            return (double) passed / caseEvaluations.size();
        }

        public Map<String, Object> toSafeMap() {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("provider", provider);
            value.put("model", model);
            value.put("modelCallCount", modelCallCount);
            value.put("rawProviderOutputStored", rawProviderOutputStored);
            value.put("casePassRate", String.format(Locale.ROOT, "%.4f", casePassRate()));
            value.put("caseSummaries", caseEvaluations.stream().map(ProviderCaseEvaluation::toSafeMap).toList());
            value.put("notes", List.of(
                    "Provider memory extraction eval stores summary only",
                    "No raw conversation text, memory content, provider output, prompt, token, or credential is stored"
            ));
            return value;
        }
    }

    public record ProviderCaseEvaluation(
            String id,
            String category,
            int extractedSuggestionCount,
            List<String> suggestionTypes,
            boolean suggestionTypesHit,
            boolean suggestionSafetyHit,
            boolean forbiddenMarkerAbsent,
            boolean passed,
            List<String> failureReasons
    ) {
        public ProviderCaseEvaluation {
            id = id == null ? "" : id.trim();
            category = category == null ? "" : category.trim();
            suggestionTypes = suggestionTypes == null ? List.of() : List.copyOf(suggestionTypes);
            failureReasons = failureReasons == null ? List.of() : List.copyOf(failureReasons);
        }

        public Map<String, Object> toSafeMap() {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("id", id);
            value.put("category", category);
            value.put("extractedSuggestionCount", extractedSuggestionCount);
            value.put("suggestionTypes", suggestionTypes);
            value.put("suggestionTypesHit", suggestionTypesHit);
            value.put("suggestionSafetyHit", suggestionSafetyHit);
            value.put("forbiddenMarkerAbsent", forbiddenMarkerAbsent);
            value.put("passed", passed);
            if (!failureReasons.isEmpty()) {
                value.put("failureReasons", failureReasons);
            }
            return value;
        }
    }
}
