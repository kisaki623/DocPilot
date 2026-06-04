package com.docpilot.backend.ai.agent.tool.openai;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record OpenAiFunctionParameters(String type,
                                       Map<String, OpenAiFunctionParameterProperty> properties,
                                       List<String> required) {

    public OpenAiFunctionParameters {
        type = type == null || type.isBlank() ? "object" : type.trim();
        properties = properties == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(properties));
        required = required == null ? List.of() : List.copyOf(required);
    }
}
