package com.docpilot.backend.auth.interceptor;

import com.docpilot.backend.common.context.UserHolder;
import com.docpilot.backend.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginInterceptorTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private LoginInterceptor loginInterceptor;

    @BeforeEach
    void setUp() {
        loginInterceptor = new LoginInterceptor(stringRedisTemplate);
        UserHolder.clear();
    }

    @AfterEach
    void tearDown() {
        UserHolder.clear();
    }

    @Test
    void shouldRejectWhenTokenMissing() {
        HttpServletRequest request = new MockHttpServletRequest();
        HttpServletResponse response = new MockHttpServletResponse();

        assertThrows(BusinessException.class, () -> loginInterceptor.preHandle(request, response, new Object()));
    }

    @Test
    void shouldRejectWhenTokenNotFoundInRedis() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer abc");
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("docpilot:auth:token:abc")).thenReturn(null);

        assertThrows(BusinessException.class,
                () -> loginInterceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
    }

    @Test
    void shouldSetAndClearUserHolderWhenTokenValid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer validToken");
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("docpilot:auth:token:validToken")).thenReturn("123");

        boolean passed = loginInterceptor.preHandle(request, new MockHttpServletResponse(), new Object());
        assertTrue(passed);
        assertEquals(123L, UserHolder.getUserId());

        loginInterceptor.afterCompletion(request, new MockHttpServletResponse(), new Object(), null);
        assertNull(UserHolder.getUserId());
    }
}


