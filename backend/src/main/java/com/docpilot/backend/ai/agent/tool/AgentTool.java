package com.docpilot.backend.ai.agent.tool;

public interface AgentTool<I, O> {

    String getToolName();

    O execute(I input);
}
