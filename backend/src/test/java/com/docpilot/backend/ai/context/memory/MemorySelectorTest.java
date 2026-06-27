package com.docpilot.backend.ai.context.memory;

import com.docpilot.backend.ai.context.ContextItem;
import com.docpilot.backend.ai.context.ContextType;
import com.docpilot.backend.ai.context.token.TokenEstimator;
import com.docpilot.backend.memory.constant.UserMemoryStatus;
import com.docpilot.backend.memory.constant.UserMemoryType;
import com.docpilot.backend.memory.entity.UserMemory;
import com.docpilot.backend.memory.mapper.UserMemoryMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemorySelectorTest {

    private final UserMemoryMapper userMemoryMapper = mock(UserMemoryMapper.class);
    private final MemorySelector selector = new MemorySelector(userMemoryMapper, new TokenEstimator());

    @Test
    void shouldOnlyUseActiveUserMemoryInContext() {
        UserMemory active = memory(1L, UserMemoryStatus.ACTIVE, "偏好中文回答");
        UserMemory suggested = memory(2L, UserMemoryStatus.SUGGESTED, "候选记忆不应进入上下文");
        UserMemory ignored = memory(3L, UserMemoryStatus.IGNORED, "已忽略记忆不应进入上下文");
        when(userMemoryMapper.selectActiveByUser(7L, null, 5)).thenReturn(List.of(active, suggested, ignored));

        List<ContextItem> items = selector.select(7L, 5);

        assertThat(items).hasSize(1);
        assertThat(items.get(0).type()).isEqualTo(ContextType.MEMORY);
        assertThat(items.get(0).content()).contains("偏好中文回答");
        assertThat(items.get(0).content()).doesNotContain("候选记忆");
        verify(userMemoryMapper).markUsed(eq(7L), eq(1L), any());
        verify(userMemoryMapper, never()).markUsed(eq(7L), eq(2L), any());
        verify(userMemoryMapper, never()).markUsed(eq(7L), eq(3L), any());
    }

    private UserMemory memory(Long id, String status, String content) {
        UserMemory memory = new UserMemory();
        memory.setId(id);
        memory.setUserId(7L);
        memory.setMemoryType(UserMemoryType.PREFERENCE);
        memory.setContent(content);
        memory.setStatus(status);
        memory.setPriority(40);
        return memory;
    }
}
