package com.docpilot.backend.ai.context.render;

import com.docpilot.backend.ai.context.ContextItem;
import com.docpilot.backend.ai.context.ContextType;
import com.docpilot.backend.ai.context.PromptMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class PromptRenderer {

    public List<PromptMessage> renderMessages(List<ContextItem> items) {
        List<PromptMessage> messages = new ArrayList<>();
        StringBuilder userContext = new StringBuilder();
        for (ContextItem item : items == null ? List.<ContextItem>of() : items) {
            if (item.type() == ContextType.SYSTEM || item.type() == ContextType.MODE_INSTRUCTION) {
                messages.add(new PromptMessage("system", item.content()));
                continue;
            }
            appendBlock(userContext, item);
        }
        if (!userContext.isEmpty()) {
            messages.add(new PromptMessage("user", userContext.toString()));
        }
        return List.copyOf(messages);
    }

    public String renderContext(List<ContextItem> items) {
        StringBuilder builder = new StringBuilder();
        for (ContextItem item : items == null ? List.<ContextItem>of() : items) {
            appendBlock(builder, item);
        }
        return builder.toString().trim();
    }

    private void appendBlock(StringBuilder builder, ContextItem item) {
        if (item.content().isBlank()) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append("\n\n");
        }
        builder.append('[').append(blockName(item.type())).append("]\n");
        builder.append(item.content());
    }

    private String blockName(ContextType type) {
        return switch (type) {
            case SYSTEM -> "System";
            case MODE_INSTRUCTION -> "Mode Instruction";
            case MEMORY -> "User Memories";
            case SUMMARY -> "Conversation Summary";
            case RECENT_TURN -> "Recent Turns";
            case RAG_EVIDENCE -> "Knowledge Base Evidence";
            case CURRENT_MESSAGE -> "Current User Message";
            case OUTPUT_REQUIREMENT -> "Output Requirements";
        };
    }
}
