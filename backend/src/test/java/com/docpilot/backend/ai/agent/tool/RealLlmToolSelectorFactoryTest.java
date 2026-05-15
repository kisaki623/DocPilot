package com.docpilot.backend.ai.agent.tool;

import com.docpilot.backend.ai.agent.config.AgentSelectorProperties;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RealLlmToolSelectorFactoryTest {

    private final RealLlmToolSelectorFactory factory = new RealLlmToolSelectorFactory(
            new LlmToolSelectionClientFactory(),
            new LlmToolSelectionPromptBuilder(),
            new LlmToolSelectionParser(Set.of(
                    "document_status_tool",
                    "document_summary_tool",
                    "document_qa_tool"
            ))
    );
    private final List<ToolDefinition> toolDefinitions = List.of(
            new ToolDefinition("document_status_tool", "Document status", "Checks parse status.", "{}", "{}", true),
            new ToolDefinition("document_summary_tool", "Document summary", "Returns summary.", "{}", "{}", true),
            new ToolDefinition("document_qa_tool", "Document QA", "Answers with citations.", "{}", "{}", true)
    );

    @Test
    void shouldCreateDisabledSelectorByDefault() {
        RealLlmToolSelector selector = factory.create(new AgentSelectorProperties());

        assertThatThrownBy(() -> selector.selectWithPrompt(
                "summarize this document",
                true,
                true,
                toolDefinitions
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("disabled");
    }

    @Test
    void shouldCreateFakeSelectorForFakeProvider() {
        AgentSelectorProperties properties = new AgentSelectorProperties();
        properties.setLlmProvider("fake");

        RealLlmToolSelector selector = factory.create(properties);
        LlmToolSelectionResult result = selector.selectWithPrompt(
                "summarize this document",
                true,
                true,
                toolDefinitions
        );

        assertThat(result.decision()).isEqualTo("summary_tool");
        assertThat(result.toolNames()).containsExactly("document_status_tool", "document_summary_tool");
    }

    @Test
    void shouldCreateDryRunOpenAiCompatibleSelector() {
        AgentSelectorProperties properties = new AgentSelectorProperties();
        properties.setLlmProvider("openai_compatible");
        properties.setLlmModel("selector-model");

        RealLlmToolSelector selector = factory.create(properties);

        assertThatThrownBy(() -> selector.selectWithPrompt(
                "answer with evidence",
                true,
                false,
                toolDefinitions
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("disabled");
    }

    @Test
    void shouldFallbackDisabledForUnknownProvider() {
        AgentSelectorProperties properties = new AgentSelectorProperties() {
            @Override
            public String getLlmProvider() {
                return "unknown";
            }
        };

        RealLlmToolSelector selector = factory.create(properties);

        assertThatThrownBy(() -> selector.selectWithPrompt(
                "summarize this document",
                true,
                true,
                toolDefinitions
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("disabled");
    }
}
