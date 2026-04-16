package com.docpilot.backend.auth.service;

import com.docpilot.backend.auth.dto.LoginRequest;
import com.docpilot.backend.auth.dto.PasswordLoginRequest;
import com.docpilot.backend.auth.dto.RegisterRequest;
import com.docpilot.backend.auth.dto.SendCodeRequest;
import com.docpilot.backend.auth.service.impl.AuthServiceImpl;
import com.docpilot.backend.auth.vo.LoginResponse;
import com.docpilot.backend.auth.vo.SendCodeResponse;
import com.docpilot.backend.common.error.ErrorCode;
import com.docpilot.backend.common.exception.BusinessException;
import com.docpilot.backend.user.entity.User;
import com.docpilot.backend.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private SmsCodeService smsCodeService;

    @Mock
    private TokenService tokenService;

    @Mock
    private UserService userService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthServiceImpl authService;

    @Test
    void shouldSendCode() {
        SendCodeRequest request = new SendCodeRequest();
        request.setPhone("13800000000");
        when(smsCodeService.sendLoginCode("13800000000")).thenReturn("123456");

        SendCodeResponse response = authService.sendCode(request);

        assertEquals("13800000000", response.getPhone());
        assertEquals("123456", response.getDevCode());
        verify(smsCodeService).sendLoginCode("13800000000");
    }

    @Test
    void shouldLoginWithExistingUser() {
        LoginRequest request = new LoginRequest();
        request.setPhone("13800000000");
        request.setCode("123456");

        User user = new User();
        user.setId(1L);
        user.setUsername("u_13800000000");
        user.setPhone("13800000000");
        user.setNickname("user-0000");

        when(userService.findByPhone("13800000000")).thenReturn(user);
        when(tokenService.createLoginToken(user)).thenReturn("token-1");

        LoginResponse response = authService.login(request);

        assertEquals("token-1", response.getToken());
        assertEquals(1L, response.getUserId());
        verify(smsCodeService).verifyLoginCode("13800000000", "123456");
    }

    @Test
    void shouldAutoCreateUserWhenNotFound() {
        LoginRequest request = new LoginRequest();
        request.setPhone("13900000000");
        request.setCode("654321");

        User created = new User();
        created.setId(2L);
        created.setUsername("u_13900000000");
        created.setPhone("13900000000");
        created.setNickname("user-0000");

        when(userService.findByPhone("13900000000")).thenReturn(null);
        when(userService.createMinimalUserByPhone("13900000000")).thenReturn(created);
        when(tokenService.createLoginToken(created)).thenReturn("token-2");

        LoginResponse response = authService.login(request);

        assertEquals("token-2", response.getToken());
        assertEquals(2L, response.getUserId());
        verify(userService).createMinimalUserByPhone("13900000000");
    }

    @Test
    void shouldRegisterAndLoginImmediately() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("alice_demo");
        request.setPassword("Password@123");
        request.setNickname("Alice");

        User user = new User();
        user.setId(10L);
        user.setUsername("alice_demo");
        user.setNickname("Alice");

        when(passwordEncoder.encode("Password@123")).thenReturn("bcrypt-hash");
        when(userService.registerByUsername("alice_demo", "bcrypt-hash", "Alice")).thenReturn(user);
        when(tokenService.createLoginToken(user)).thenReturn("token-register");

        LoginResponse response = authService.register(request);

        assertEquals("token-register", response.getToken());
        assertEquals(10L, response.getUserId());
        assertEquals("alice_demo", response.getUsername());
    }

    @Test
    void shouldRejectRegisterWhenPasswordTooShort() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("alice_demo");
        request.setPassword("1234567");

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.register(request));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
    }

    @Test
    void shouldLoginByPassword() {
        PasswordLoginRequest request = new PasswordLoginRequest();
        request.setUsername("alice_demo");
        request.setPassword("Password@123");

        User user = new User();
        user.setId(11L);
        user.setUsername("alice_demo");
        user.setNickname("Alice");
        user.setPasswordHash("$2a$10$dummy");

        when(userService.findByUsername("alice_demo")).thenReturn(user);
        when(passwordEncoder.matches("Password@123", "$2a$10$dummy")).thenReturn(true);
        when(tokenService.createLoginToken(user)).thenReturn("token-password");

        LoginResponse response = authService.loginByPassword(request);

        assertEquals("token-password", response.getToken());
        assertEquals("alice_demo", response.getUsername());
    }

    @Test
    void shouldRejectPasswordLoginWhenPasswordInvalid() {
        PasswordLoginRequest request = new PasswordLoginRequest();
        request.setUsername("alice_demo");
        request.setPassword("WrongPassword");

        User user = new User();
        user.setPasswordHash("$2a$10$dummy");

        when(userService.findByUsername("alice_demo")).thenReturn(user);
        when(passwordEncoder.matches("WrongPassword", "$2a$10$dummy")).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.loginByPassword(request));
        assertEquals(ErrorCode.USERNAME_OR_PASSWORD_INVALID, ex.getErrorCode());
    }
}

