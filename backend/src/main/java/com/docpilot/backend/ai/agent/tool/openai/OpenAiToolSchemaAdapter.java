package com.docpilot.backend.ai.agent.tool.openai;

import com.docpilot.backend.ai.agent.tool.spec.ToolParameterSchema;
import com.docpilot.backend.ai.agent.tool.spec.ToolSpec;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class OpenAiToolSchemaAdapter {

    public List<OpenAiToolDefinition> toTools(List<ToolSpec> specs) {
        return (specs == null ? List.<ToolSpec>of() : specs).stream()
                .filter(ToolSpec::safeForLlmSelection)
                .map(this::toTool)
                .toList();
    }

    public OpenAiToolDefinition toTool(ToolSpec spec) {
        if (spec == null) {
            throw new IllegalArgumentException("spec must not be null");
        }
        return new OpenAiToolDefinition(
                "function",
                new OpenAiFunctionDefinition(
                        spec.name(),
                        spec.description(),
                        toParameters(spec.parameterSchema(), spec.requiredFields().stream().toList())
                )
        );
    }

    private OpenAiFunctionParameters toParameters(ToolParameterSchema schema, List<String> requiredFields) {
        Map<String, OpenAiFunctionParameterProperty> properties = new LinkedHashMap<>();
        if (schema != null) {
            for (Map.Entry<String, String> entry : schema.properties().entrySet()) {
                properties.put(entry.getKey(), toProperty(entry.getValue()));
            }
        }
        return new OpenAiFunctionParameters("object", properties, requiredFields);
    }

    private OpenAiFunctionParameterProperty toProperty(String typeText) {
        String rawType = typeText == null ? "" : typeText.trim();
        return new OpenAiFunctionParameterProperty(toJsonSchemaType(rawType), "Internal type: " + rawType);
    }

    private String toJsonSchemaType(String rawType) {
        String normalized = rawType.toLowerCase(java.util.Locale.ROOT);
        if (normalized.startsWith("long") || normalized.startsWith("int")) {
            return "integer";
        }
        if (normalized.startsWith("boolean")) {
            return "boolean";
        }
        if (normalized.startsWith("list")) {
            return "array";
        }
        return "string";
    }
}
