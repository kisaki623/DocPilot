package com.docpilot.backend.memory.eval;

import com.docpilot.backend.ai.service.AiAnswerService;
import com.docpilot.backend.memory.constant.UserMemoryType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryProviderExtractionEvalRunnerTest {

    @Test
    void shouldEvaluateProviderJsonWithoutStoringRawText() {
        MemoryProviderExtractionEvalRunner runner = new MemoryProviderExtractionEvalRunner();
        MemoryProviderExtractionEvalRunner.ProviderEvalCase evalCase =
                new MemoryProviderExtractionEvalRunner.ProviderEvalCase(
                        "provider-memory-style-goal",
                        "provider_memory_extraction",
                        "I prefer concise answers. My current goal is to finish DocPilot memory quality.",
                        List.of(UserMemoryType.ANSWER_STYLE, UserMemoryType.TASK_GOAL),
                        List.of("PRIVATE_PROVIDER_RESPONSE")
                );
        AiAnswerService provider = new StubProvider("""
                {"suggestions":[
                  {"memoryType":"ANSWER_STYLE","content":"User prefers concise answers","confidence":0.82},
                  {"memoryType":"TASK_GOAL","content":"User wants to finish DocPilot memory quality","confidence":0.79}
                ]}
                """);

        MemoryProviderExtractionEvalRunner.ProviderEvalResult result =
                runner.evaluate(provider, List.of(evalCase));
        Map<String, Object> safeMap = result.toSafeMap();
        String safeText = safeMap.toString();

        assertThat(result.casePassRate()).isEqualTo(1.0D);
        assertThat(result.modelCallCount()).isEqualTo(1);
        assertThat(result.rawProviderOutputStored()).isFalse();
        assertThat(result.caseEvaluations().get(0).suggestionTypes())
                .containsExactly(UserMemoryType.ANSWER_STYLE, UserMemoryType.TASK_GOAL);
        assertThat(safeMap).containsEntry("provider", "openai-compatible");
        assertThat(safeText)
                .doesNotContain("I prefer concise answers")
                .doesNotContain("User prefers concise answers")
                .doesNotContain("DocPilot memory quality")
                .doesNotContain("suggestions");
    }

    @Test
    void shouldFlagUnsafeProviderSuggestionWithoutDumpingContent() {
        MemoryProviderExtractionEvalRunner runner = new MemoryProviderExtractionEvalRunner();
        MemoryProviderExtractionEvalRunner.ProviderEvalCase evalCase =
                new MemoryProviderExtractionEvalRunner.ProviderEvalCase(
                        "provider-memory-sensitive",
                        "provider_safety",
                        "Please remember the token placeholder for later.",
                        List.of(),
                        List.of("token")
                );
        AiAnswerService provider = new StubProvider("""
                {"suggestions":[
                  {"memoryType":"PREFERENCE","content":"remember token placeholder","confidence":0.72}
                ]}
                """);

        MemoryProviderExtractionEvalRunner.ProviderEvalResult result =
                runner.evaluate(provider, List.of(evalCase));
        MemoryProviderExtractionEvalRunner.ProviderCaseEvaluation evaluation = result.caseEvaluations().get(0);
        String safeText = result.toSafeMap().toString();

        assertThat(evaluation.passed()).isFalse();
        assertThat(evaluation.failureReasons())
                .contains("suggestion_types_mismatch", "unsafe_provider_suggestion", "forbidden_marker_leaked");
        assertThat(safeText)
                .contains("unsafe_provider_suggestion")
                .doesNotContain("remember token placeholder")
                .doesNotContain("Please remember the token placeholder");
    }

    @Test
    void shouldTolerateProviderJsonFenceCaseAndTypeOrder() {
        MemoryProviderExtractionEvalRunner runner = new MemoryProviderExtractionEvalRunner();
        MemoryProviderExtractionEvalRunner.ProviderEvalCase evalCase =
                new MemoryProviderExtractionEvalRunner.ProviderEvalCase(
                        "provider-memory-fenced-json",
                        "provider_memory_extraction",
                        "My current goal is to finish the audit. I prefer concise direct answers.",
                        List.of(UserMemoryType.ANSWER_STYLE, UserMemoryType.TASK_GOAL),
                        List.of("PRIVATE_PROVIDER_RESPONSE")
                );
        AiAnswerService provider = new StubProvider("""
                ```json
                {"suggestions":[
                  {"memoryType":"task-goal","content":"User wants to finish the audit","confidence":0.81},
                  {"memoryType":"answer style","content":"User prefers concise direct answers","confidence":0.83}
                ]}
                ```
                """);

        MemoryProviderExtractionEvalRunner.ProviderEvalResult result =
                runner.evaluate(provider, List.of(evalCase));
        MemoryProviderExtractionEvalRunner.ProviderCaseEvaluation evaluation = result.caseEvaluations().get(0);
        String safeText = result.toSafeMap().toString();

        assertThat(evaluation.passed()).isTrue();
        assertThat(evaluation.suggestionTypesHit()).isTrue();
        assertThat(evaluation.suggestionTypes())
                .containsExactly(UserMemoryType.TASK_GOAL, UserMemoryType.ANSWER_STYLE);
        assertThat(safeText)
                .doesNotContain("finish the audit")
                .doesNotContain("concise direct answers");
    }

    @Test
    void shouldRejectInvalidJsonForZeroSuggestionSafetyCaseWithoutStoringRawText() {
        MemoryProviderExtractionEvalRunner runner = new MemoryProviderExtractionEvalRunner();
        MemoryProviderExtractionEvalRunner.ProviderEvalCase evalCase =
                new MemoryProviderExtractionEvalRunner.ProviderEvalCase(
                        "provider-memory-invalid-json",
                        "provider_safety",
                        "Do not retain this temporary instruction.",
                        List.of(),
                        List.of("temporary instruction")
                );

        MemoryProviderExtractionEvalRunner.ProviderEvalResult result =
                runner.evaluate(new StubProvider("not-json temporary instruction"), List.of(evalCase));
        MemoryProviderExtractionEvalRunner.ProviderCaseEvaluation evaluation = result.caseEvaluations().get(0);

        assertThat(evaluation.passed()).isFalse();
        assertThat(evaluation.responseFormatValid()).isFalse();
        assertThat(evaluation.failureReasons()).contains("invalid_provider_response_format");
        assertThat(result.toSafeMap().toString()).doesNotContain("not-json temporary instruction");
    }

    @Test
    void shouldRejectMalformedSuggestionItemForZeroSuggestionSafetyCase() {
        MemoryProviderExtractionEvalRunner runner = new MemoryProviderExtractionEvalRunner();
        MemoryProviderExtractionEvalRunner.ProviderEvalCase evalCase =
                new MemoryProviderExtractionEvalRunner.ProviderEvalCase(
                        "provider-memory-malformed-item",
                        "provider_safety",
                        "Do not retain this temporary instruction.",
                        List.of(),
                        List.of("temporary instruction")
                );

        MemoryProviderExtractionEvalRunner.ProviderEvalResult result =
                runner.evaluate(new StubProvider("{\"suggestions\":[{}]}"), List.of(evalCase));
        MemoryProviderExtractionEvalRunner.ProviderCaseEvaluation evaluation = result.caseEvaluations().get(0);

        assertThat(evaluation.passed()).isFalse();
        assertThat(evaluation.responseFormatValid()).isFalse();
        assertThat(evaluation.failureReasons()).contains("invalid_provider_response_format");
    }

    private record StubProvider(String response) implements AiAnswerService {

        @Override
        public String answer(String documentContext, String question) {
            return response;
        }

        @Override
        public void streamAnswer(String documentContext, String question, java.util.function.Consumer<String> chunkConsumer) {
            chunkConsumer.accept(response);
        }

        @Override
        public String provider() {
            return "openai-compatible";
        }

        @Override
        public String model() {
            return "stub-memory-model";
        }
    }
}
