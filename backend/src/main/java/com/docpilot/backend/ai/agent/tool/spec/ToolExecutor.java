package com.docpilot.backend.ai.agent.tool.spec;

import java.util.Map;

public interface ToolExecutor {

    ToolSpec spec();

    ToolCallResult execute(ToolExecutionContext context, Map<String, Object> arguments);
}
