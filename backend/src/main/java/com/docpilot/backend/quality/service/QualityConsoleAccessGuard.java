package com.docpilot.backend.quality.service;

import com.docpilot.backend.common.context.UserHolder;
import com.docpilot.backend.common.error.ErrorCode;
import com.docpilot.backend.common.exception.BusinessException;
import com.docpilot.backend.quality.mapper.QualityRunMapper;
import com.docpilot.backend.quality.vo.QualityConsoleStatus;
import com.docpilot.backend.user.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Component
public class QualityConsoleAccessGuard {

    private final UserMapper userMapper;
    private final QualityRunMapper qualityRunMapper;
    private final boolean consoleEnabled;
    private final String environment;

    public QualityConsoleAccessGuard(
            UserMapper userMapper,
            QualityRunMapper qualityRunMapper,
            @Value("${app.quality.console.enabled:false}") boolean consoleEnabled,
            @Value("${app.quality.console.environment:${spring.profiles.active:local}}") String environment) {
        this.userMapper = userMapper;
        this.qualityRunMapper = qualityRunMapper;
        this.consoleEnabled = consoleEnabled;
        this.environment = environment;
    }

    public Long requireInternalAdmin() {
        if (!consoleEnabled) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "quality console is disabled");
        }
        Long userId = UserHolder.requireUserId();
        if (!isActiveInternalAdmin(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "quality console forbidden");
        }
        return userId;
    }

    public QualityConsoleStatus currentStatus() {
        Long userId = UserHolder.requireUserId();
        if (!consoleEnabled) {
            return new QualityConsoleStatus(false, false, "DISABLED", "DB", 0, null, environment);
        }
        Boolean authorized = safeActiveInternalAdmin(userId);
        if (authorized == null) {
            return new QualityConsoleStatus(true, false, "STORAGE_UNAVAILABLE", "DB", 0, null, environment);
        }
        if (!authorized) {
            return new QualityConsoleStatus(true, false, "FORBIDDEN", "DB", 0, null, environment);
        }
        try {
            return new QualityConsoleStatus(
                    true,
                    true,
                    "OK",
                    "DB",
                    qualityRunMapper.countRuns(),
                    toInstant(qualityRunMapper.selectLastImportedAt()),
                    environment
            );
        } catch (RuntimeException ex) {
            return new QualityConsoleStatus(true, true, "STORAGE_UNAVAILABLE", "DB", 0, null, environment);
        }
    }

    private Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    private boolean isActiveInternalAdmin(Long userId) {
        Boolean authorized = safeActiveInternalAdmin(userId);
        if (authorized == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "quality console storage unavailable");
        }
        return authorized;
    }

    private Boolean safeActiveInternalAdmin(Long userId) {
        try {
            return userMapper.existsActiveInternalAdmin(userId);
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
