package com.docpilot.backend.ai.context.memory;

import com.docpilot.backend.ai.context.ContextItem;
import com.docpilot.backend.ai.context.ContextType;
import com.docpilot.backend.ai.context.token.TokenEstimator;
import com.docpilot.backend.memory.constant.UserMemoryStatus;
import com.docpilot.backend.memory.entity.UserMemory;
import com.docpilot.backend.memory.mapper.UserMemoryMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class MemorySelector {

    private final UserMemoryMapper userMemoryMapper;
    private final TokenEstimator tokenEstimator;

    public MemorySelector(UserMemoryMapper userMemoryMapper,
                          TokenEstimator tokenEstimator) {
        this.userMemoryMapper = userMemoryMapper;
        this.tokenEstimator = tokenEstimator;
    }

    public List<ContextItem> select(Long userId, int maxCount) {
        if (maxCount <= 0) {
            return List.of();
        }
        List<UserMemory> memories = userMemoryMapper.selectActiveByUser(userId, null, maxCount);
        List<ContextItem> items = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (UserMemory memory : memories) {
            if (memory == null || !UserMemoryStatus.ACTIVE.equals(memory.getStatus())) {
                continue;
            }
            String content = memory.getMemoryType() + ": " + memory.getContent();
            if (userMemoryMapper.markUsed(userId, memory.getId(), now) <= 0) {
                continue;
            }
            items.add(new ContextItem(
                    ContextType.MEMORY,
                    content,
                    priorityFor(memory.getMemoryType(), memory.getPriority()),
                    tokenEstimator.estimate(content),
                    false,
                    memory.getUserId(),
                    String.valueOf(memory.getId()),
                    memory.getStatus(),
                    Map.of("memoryType", memory.getMemoryType())
            ));
        }
        return List.copyOf(items);
    }

    private int priorityFor(String memoryType, Integer priority) {
        int base = switch (memoryType == null ? "" : memoryType) {
            case "PROJECT_STATE" -> 620;
            case "PREFERENCE", "ANSWER_STYLE" -> 600;
            case "TASK_GOAL" -> 580;
            case "TECH_CONTEXT" -> 560;
            default -> 540;
        };
        return base + Math.max(0, priority == null ? 0 : priority);
    }
}
