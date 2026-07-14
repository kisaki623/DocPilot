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
import java.util.Set;

public class MemoryProviderExtractionEvalRunner {

    private static final Set<String> VALID_MEMORY_TYPES = Set.of(
            "ANSWER_STYLE", "PREFERENCE", "TASK_GOAL", "PROJECT_STATE", "TECH_CONTEXT"
    );

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
            modelCallCount++;
            try {
                evaluations.add(evaluateOne(evalCase, provider.answer(PROMPT_CONTEXT, promptQuestion(evalCase))));
            } catch (RuntimeException ignored) {
                evaluations.add(providerCallFailure(evalCase));
            }
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
        ParsedSuggestions parsed = parseSuggestions(rawAnswer);
        List<ProviderSuggestion> suggestions = parsed.suggestions();
        List<String> suggestionTypes = suggestions.stream().map(ProviderSuggestion::memoryType).toList();
        List<String> failureReasons = new ArrayList<>();

        boolean responseFormatValid = parsed.responseFormatValid();
        if (!responseFormatValid) {
            failureReasons.add("invalid_provider_response_format");
        }

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

        boolean passed = responseFormatValid && suggestionTypesHit && suggestionSafetyHit && forbiddenMarkerAbsent;
        return new ProviderCaseEvaluation(
                evalCase.id(),
                evalCase.category(),
                suggestions.size(),
                suggestionTypes,
                suggestionTypesHit,
                suggestionSafetyHit,
                forbiddenMarkerAbsent,
                responseFormatValid,
                passed,
                failureReasons
        );
    }

    private ProviderCaseEvaluation providerCallFailure(ProviderEvalCase evalCase) {
        return new ProviderCaseEvaluation(
                evalCase.id(),
                evalCase.category(),
                0,
                List.of(),
                false,
                true,
                true,
                false,
                false,
                List.of("provider_call_failed")
        );
    }

    private ParsedSuggestions parseSuggestions(String rawAnswer) {
        if (rawAnswer == null || rawAnswer.isBlank()) {
            return new ParsedSuggestions(List.of(), false);
        }
        try {
            JsonNode root = objectMapper.readTree(extractJsonPayload(rawAnswer));
            if (root == null || !root.isObject() || !root.has("suggestions") || !root.path("suggestions").isArray()) {
                return new ParsedSuggestions(List.of(), false);
            }
            JsonNode suggestionsNode = root.path("suggestions");
            List<ProviderSuggestion> suggestions = new ArrayList<>();
            for (JsonNode item : suggestionsNode) {
                if (!item.isObject()
                        || !item.path("memoryType").isTextual()
                        || !item.path("content").isTextual()
                        || !item.path("confidence").isNumber()) {
                    return new ParsedSuggestions(List.of(), false);
                }
                String memoryType = normalizeMemoryType(item.path("memoryType").asText());
                String content = item.path("content").asText().trim();
                double confidence = item.path("confidence").asDouble();
                if (memoryType.isBlank() || content.isBlank() || !VALID_MEMORY_TYPES.contains(memoryType)
                        || !Double.isFinite(confidence) || confidence < 0.0D || confidence > 1.0D) {
                    return new ParsedSuggestions(List.of(), false);
                }
                suggestions.add(new ProviderSuggestion(memoryType, content, confidence));
            }
            return new ParsedSuggestions(suggestions, true);
        } catch (Exception ignored) {
            return new ParsedSuggestions(List.of(), false);
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

    private record ParsedSuggestions(
            List<ProviderSuggestion> suggestions,
            boolean responseFormatValid
    ) {
        private ParsedSuggestions {
            suggestions = suggestions == null ? List.of() : List.copyOf(suggestions);
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
            boolean responseFormatValid,
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
            value.put("responseFormatValid", responseFormatValid);
            value.put("passed", passed);
            if (!failureReasons.isEmpty()) {
                value.put("failureReasons", failureReasons);
            }
            return value;
        }
    }
}
