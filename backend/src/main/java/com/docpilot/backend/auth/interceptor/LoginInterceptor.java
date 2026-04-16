package com.docpilot.backend.auth.interceptor;

import com.docpilot.backend.common.constant.CommonConstants;
import com.docpilot.backend.common.context.UserHolder;
import com.docpilot.backend.common.error.ErrorCode;
import com.docpilot.backend.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class LoginInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate stringRedisTemplate;

    public LoginInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String rawToken = request.getHeader(CommonConstants.AUTHORIZATION_HEADER);
        if (rawToken == null || rawToken.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "missing login token");
        }

        String token = extractToken(rawToken);
        if (token.isEmpty()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "invalid login token");
        }

        String key = CommonConstants.LOGIN_TOKEN_KEY_PREFIX + token;
        String userIdValue = stringRedisTemplate.opsForValue().get(key);
        if (userIdValue == null || userIdValue.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "expired or invalid login token");
        }

        try {
            UserHolder.setUserId(Long.parseLong(userIdValue));
            return true;
        } catch (NumberFormatException ex) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "invalid login token");
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserHolder.clear();
    }

    private String extractToken(String rawToken) {
        String token = rawToken.trim();
        if (token.startsWith(CommonConstants.BEARER_PREFIX)) {
            return token.substring(CommonConstants.BEARER_PREFIX.length()).trim();
        }
        return token;
    }
}
