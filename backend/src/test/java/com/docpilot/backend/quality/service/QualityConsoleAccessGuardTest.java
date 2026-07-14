package com.docpilot.backend.quality.service;

import com.docpilot.backend.common.context.UserHolder;
import com.docpilot.backend.common.error.ErrorCode;
import com.docpilot.backend.common.exception.BusinessException;
import com.docpilot.backend.quality.mapper.QualityRunMapper;
import com.docpilot.backend.user.mapper.UserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QualityConsoleAccessGuardTest {

    private final UserMapper userMapper = mock(UserMapper.class);
    private final QualityRunMapper qualityRunMapper = mock(QualityRunMapper.class);

    @AfterEach
    void clearUser() {
        UserHolder.clear();
    }

    @Test
    void shouldFailClosedWhenConsoleDisabled() {
        UserHolder.setUserId(7L);
        QualityConsoleAccessGuard guard = new QualityConsoleAccessGuard(userMapper, qualityRunMapper, false, "local");

        assertThatThrownBy(guard::requireInternalAdmin)
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
                    assertThat(ex.getMessage()).isEqualTo("quality console is disabled");
                });

        assertThat(guard.currentStatus().reason()).isEqualTo("DISABLED");
    }

    @Test
    void shouldRejectNonInternalAdminUser() {
        UserHolder.setUserId(7L);
        when(userMapper.existsActiveInternalAdmin(7L)).thenReturn(false);
        QualityConsoleAccessGuard guard = new QualityConsoleAccessGuard(userMapper, qualityRunMapper, true, "local");

        assertThatThrownBy(guard::requireInternalAdmin)
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
                    assertThat(ex.getMessage()).isEqualTo("quality console forbidden");
                });

        assertThat(guard.currentStatus().authorized()).isFalse();
        assertThat(guard.currentStatus().reason()).isEqualTo("FORBIDDEN");
    }

    @Test
    void shouldAllowActiveInternalAdmin() {
        UserHolder.setUserId(7L);
        when(userMapper.existsActiveInternalAdmin(7L)).thenReturn(true);
        when(qualityRunMapper.countRuns()).thenReturn(2);
        when(qualityRunMapper.selectLastImportedAt()).thenReturn(LocalDateTime.of(2026, 7, 14, 1, 2));
        QualityConsoleAccessGuard guard = new QualityConsoleAccessGuard(userMapper, qualityRunMapper, true, "local");

        assertThat(guard.requireInternalAdmin()).isEqualTo(7L);
        assertThat(guard.currentStatus().authorized()).isTrue();
        assertThat(guard.currentStatus().runCount()).isEqualTo(2);
        assertThat(guard.currentStatus().lastImportedAt()).isNotNull();
    }

    @Test
    void shouldReportStorageUnavailableWithoutLeakingDetails() {
        UserHolder.setUserId(7L);
        when(userMapper.existsActiveInternalAdmin(7L)).thenReturn(true);
        when(qualityRunMapper.countRuns()).thenThrow(new IllegalStateException("unknown table tb_quality_run"));
        QualityConsoleAccessGuard guard = new QualityConsoleAccessGuard(userMapper, qualityRunMapper, true, "local");

        assertThat(guard.currentStatus().authorized()).isTrue();
        assertThat(guard.currentStatus().reason()).isEqualTo("STORAGE_UNAVAILABLE");
        assertThat(guard.currentStatus().runCount()).isZero();
    }
}
