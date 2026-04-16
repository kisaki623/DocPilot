package com.docpilot.backend.auth.service;

import com.docpilot.backend.auth.service.impl.SmsCodeServiceImpl;
import com.docpilot.backend.common.constant.CommonConstants;
import com.docpilot.backend.common.error.ErrorCode;
import com.docpilot.backend.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SmsCodeServiceImplTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Test
    void shouldSendCodeWhenUnderRateLimit() {
        SmsCodeServiceImpl smsCodeService = new SmsCodeServiceImpl(stringRedisTemplate);
        String phone = "13800000000";

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(CommonConstants.buildSmsCodeRateLimitKey(phone))).thenReturn(1L);

        String code = smsCodeService.sendLoginCode(phone);

        assertNotNull(code);
        assertEquals(6, code.length());
        verify(stringRedisTemplate).expire(
                CommonConstants.buildSmsCodeRateLimitKey(phone),
                CommonConstants.SMS_CODE_RATE_LIMIT_WINDOW_SECONDS,
                TimeUnit.SECONDS
        );
        verify(valueOperations).set(
                eq(CommonConstants.LOGIN_SMS_CODE_KEY_PREFIX + phone),
                eq(code),
                eq(CommonConstants.LOGIN_SMS_CODE_TTL_SECONDS),
                eq(TimeUnit.SECONDS)
        );
    }

    @Test
    void shouldRejectWhenRateLimitExceeded() {
        SmsCodeServiceImpl smsCodeService = new SmsCodeServiceImpl(stringRedisTemplate);
        String phone = "13800000000";

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(CommonConstants.buildSmsCodeRateLimitKey(phone))).thenReturn(2L);

        BusinessException ex = assertThrows(BusinessException.class, () -> smsCodeService.sendLoginCode(phone));

        assertEquals(ErrorCode.RATE_LIMIT_EXCEEDED, ex.getErrorCode());
        assertEquals("验证码发送过于频繁，请1分钟后再试", ex.getMessage());
        verify(valueOperations, never()).set(
                eq(CommonConstants.LOGIN_SMS_CODE_KEY_PREFIX + phone),
                anyString(),
                eq(CommonConstants.LOGIN_SMS_CODE_TTL_SECONDS),
                eq(TimeUnit.SECONDS)
        );
    }

    @Test
    void shouldRateLimitByPhoneIndependently() {
        SmsCodeServiceImpl smsCodeService = new SmsCodeServiceImpl(stringRedisTemplate);
        String phoneA = "13800000000";
        String phoneB = "13900000000";

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(CommonConstants.buildSmsCodeRateLimitKey(phoneA))).thenReturn(1L);
        when(valueOperations.increment(CommonConstants.buildSmsCodeRateLimitKey(phoneB))).thenReturn(1L);

        smsCodeService.sendLoginCode(phoneA);
        smsCodeService.sendLoginCode(phoneB);

        verify(valueOperations).increment(CommonConstants.buildSmsCodeRateLimitKey(phoneA));
        verify(valueOperations).increment(CommonConstants.buildSmsCodeRateLimitKey(phoneB));
        verify(stringRedisTemplate).expire(
                CommonConstants.buildSmsCodeRateLimitKey(phoneA),
                CommonConstants.SMS_CODE_RATE_LIMIT_WINDOW_SECONDS,
                TimeUnit.SECONDS
        );
        verify(stringRedisTemplate).expire(
                CommonConstants.buildSmsCodeRateLimitKey(phoneB),
                CommonConstants.SMS_CODE_RATE_LIMIT_WINDOW_SECONDS,
                TimeUnit.SECONDS
        );
    }
}


