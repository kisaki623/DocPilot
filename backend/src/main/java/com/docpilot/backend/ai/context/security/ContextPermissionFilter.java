package com.docpilot.backend.ai.context.security;

import com.docpilot.backend.ai.context.ContextItem;
import com.docpilot.backend.ai.context.ContextType;
import com.docpilot.backend.common.error.ErrorCode;
import com.docpilot.backend.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ContextPermissionFilter {

    public List<ContextItem> filter(Long currentUserId, List<ContextItem> items) {
        List<ContextItem> filtered = new ArrayList<>();
        for (ContextItem item : items == null ? List.<ContextItem>of() : items) {
            if (item.content().isBlank()) {
                continue;
            }
            if (!item.activeOrSystem()) {
                continue;
            }
            if (item.type() != ContextType.SYSTEM
                    && item.type() != ContextType.MODE_INSTRUCTION
                    && item.type() != ContextType.OUTPUT_REQUIREMENT
                    && item.ownerUserId() != null
                    && !currentUserId.equals(item.ownerUserId())) {
                throw new BusinessException(ErrorCode.CONVERSATION_FORBIDDEN,
                        "context item is outside current user scope");
            }
            filtered.add(item);
        }
        return List.copyOf(filtered);
    }
}
