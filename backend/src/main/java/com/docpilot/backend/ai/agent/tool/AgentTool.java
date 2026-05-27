package com.docpilot.backend.ai.agent.tool;

/**
 * Unified contract for Agent tools.
 *
 * <p>To add a new tool, implement {@code AgentTool<I, O>}, register it as a
 * Spring {@code @Component}, and let {@link ToolRegistry} collect it by
 * {@link #getToolName()}. Then add the corresponding routing rule in
 * {@link ToolSelector} / {@link DocumentToolSelector}.</p>
 *
 * @param <I> tool input type
 * @param <O> tool output type
 */
public interface AgentTool<I, O> {

    String getToolName();

    O execute(I input);
}
