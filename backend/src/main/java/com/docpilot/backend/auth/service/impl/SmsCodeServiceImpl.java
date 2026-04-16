package com.docpilot.backend.auth.service.impl;

import com.docpilot.backend.auth.service.SmsCodeService;
import com.docpilot.backend.common.constant.CommonConstants;
import com.docpilot.backend.common.error.ErrorCode;
import com.docpilot.backend.common.exception.BusinessException;
import com.docpilot.backend.common.metrics.DocPilotMetrics;
import com.docpilot.backend.common.util.ValidationUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Service
public class SmsCodeServiceImpl implements SmsCodeService {

    private final StringRedisTemplate stringRedisTemplate;

    public SmsCodeServiceImpl(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public String sendLoginCode(String phone) {
        ValidationUtils.requireNonBlank(phone, "phone");
        checkSendCodeRateLimit(phone);

        String code = String.format("%06d", ThreadLocalRandom.current().nextInt(1000000));
        String key = CommonConstants.LOGIN_SMS_CODE_KEY_PREFIX + phone;
        stringRedisTemplate.opsForValue().set(key, code, CommonConstants.LOGIN_SMS_CODE_TTL_SECONDS, TimeUnit.SECONDS);
        return code;
    }

    @Override
    public void verifyLoginCode(String phone, String code) {
        ValidationUtils.requireNonBlank(phone, "phone");
        ValidationUtils.requireNonBlank(code, "code");

        String key = CommonConstants.LOGIN_SMS_CODE_KEY_PREFIX + phone;
        String cachedCode = stringRedisTemplate.opsForValue().get(key);
        if (cachedCode == null || cachedCode.isEmpty()) {
            throw new BusinessException(ErrorCode.SMS_CODE_EXPIRED);
        }
        if (!cachedCode.equals(code)) {
            throw new BusinessException(ErrorCode.SMS_CODE_INVALID);
        }

        // One-time code semantics for the simplest secure behavior.
        stringRedisTemplate.delete(key);
    }

    private void checkSendCodeRateLimit(String phone) {
        String key = CommonConstants.buildSmsCodeRateLimitKey(phone);
        Long currentCount = stringRedisTemplate.opsForValue().increment(key);
        if (currentCount == null) {
            return;
        }
        if (currentCount == 1L) {
            stringRedisTemplate.expire(key, CommonConstants.SMS_CODE_RATE_LIMIT_WINDOW_SECONDS, TimeUnit.SECONDS);
            return;
        }
        if (currentCount > CommonConstants.SMS_CODE_RATE_LIMIT_MAX_REQUESTS) {
            DocPilotMetrics.recordRateLimitTrigger("sms_code");
            throw new BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED, "验证码发送过于频繁，请1分钟后再试");
        }
    }
}

