package com.docpilot.backend.ai.context;

import com.docpilot.backend.ai.context.security.ContextPermissionFilter;
import com.docpilot.backend.common.error.ErrorCode;
import com.docpilot.backend.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ContextPermissionFilterTest {

    private final ContextPermissionFilter filter = new ContextPermissionFilter();

    @Test
    void shouldDropDeletedItems() {
        List<ContextItem> result = filter.filter(7L, List.of(
                item(ContextType.MEMORY, 7L, "ACTIVE"),
                item(ContextType.MEMORY, 7L, "DELETED")
        ));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).status()).isEqualTo("ACTIVE");
    }

    @Test
    void shouldRejectCrossUserItems() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> filter.filter(7L, List.of(item(ContextType.MEMORY, 8L, "ACTIVE"))));

        assertEquals(ErrorCode.CONVERSATION_FORBIDDEN, ex.getErrorCode());
    }

    private ContextItem item(ContextType type, Long ownerUserId, String status) {
        return new ContextItem(type, "content", 1, 1, false, ownerUserId, "source", status, Map.of());
    }
}
