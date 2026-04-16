package com.docpilot.backend.auth.service.impl;

import com.docpilot.backend.auth.service.TokenService;
import com.docpilot.backend.common.constant.CommonConstants;
import com.docpilot.backend.common.util.ValidationUtils;
import com.docpilot.backend.user.entity.User;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class TokenServiceImpl implements TokenService {

    private final StringRedisTemplate stringRedisTemplate;

    public TokenServiceImpl(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public String createLoginToken(User user) {
        ValidationUtils.requireNonNull(user, "user");
        ValidationUtils.requireNonNull(user.getId(), "user.id");

        String token = UUID.randomUUID().toString().replace("-", "");
        String key = CommonConstants.LOGIN_TOKEN_KEY_PREFIX + token;
        String value = String.valueOf(user.getId());

        stringRedisTemplate.opsForValue().set(key, value, CommonConstants.LOGIN_TOKEN_TTL_SECONDS, TimeUnit.SECONDS);
        return token;
    }
}

