package com.docpilot.backend.auth.service.impl;

import com.docpilot.backend.auth.dto.LoginRequest;
import com.docpilot.backend.auth.dto.PasswordLoginRequest;
import com.docpilot.backend.auth.dto.RegisterRequest;
import com.docpilot.backend.auth.dto.SendCodeRequest;
import com.docpilot.backend.auth.service.AuthService;
import com.docpilot.backend.auth.service.SmsCodeService;
import com.docpilot.backend.auth.service.TokenService;
import com.docpilot.backend.auth.vo.LoginResponse;
import com.docpilot.backend.auth.vo.SendCodeResponse;
import com.docpilot.backend.common.error.ErrorCode;
import com.docpilot.backend.common.exception.BusinessException;
import com.docpilot.backend.user.entity.User;
import com.docpilot.backend.user.service.UserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthServiceImpl implements AuthService {

    private final SmsCodeService smsCodeService;
    private final TokenService tokenService;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;

    public AuthServiceImpl(SmsCodeService smsCodeService,
                           TokenService tokenService,
                           UserService userService,
                           PasswordEncoder passwordEncoder) {
        this.smsCodeService = smsCodeService;
        this.tokenService = tokenService;
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public SendCodeResponse sendCode(SendCodeRequest request) {
        String code = smsCodeService.sendLoginCode(request.getPhone());
        return new SendCodeResponse(request.getPhone(), code);
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        smsCodeService.verifyLoginCode(request.getPhone(), request.getCode());

        User user = userService.findByPhone(request.getPhone());
        if (user == null) {
            user = userService.createMinimalUserByPhone(request.getPhone());
        }

        String token = tokenService.createLoginToken(user);
        return new LoginResponse(token, user.getId(), user.getUsername(), user.getPhone(), user.getNickname());
    }

    @Override
    public LoginResponse register(RegisterRequest request) {
        String username = request.getUsername().trim();
        String rawPassword = request.getPassword();
        if (rawPassword.length() < 8) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "密码长度至少 8 位");
        }
        String passwordHash = passwordEncoder.encode(rawPassword);
        User user = userService.registerByUsername(username, passwordHash, request.getNickname());
        String token = tokenService.createLoginToken(user);
        return new LoginResponse(token, user.getId(), user.getUsername(), user.getPhone(), user.getNickname());
    }

    @Override
    public LoginResponse loginByPassword(PasswordLoginRequest request) {
        String username = request.getUsername().trim();
        User user = userService.findByUsername(username);
        if (user == null || !passwordMatches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.USERNAME_OR_PASSWORD_INVALID);
        }
        String token = tokenService.createLoginToken(user);
        return new LoginResponse(token, user.getId(), user.getUsername(), user.getPhone(), user.getNickname());
    }

    private boolean passwordMatches(String rawPassword, String encodedPassword) {
        if (encodedPassword == null || encodedPassword.trim().isEmpty()) {
            return false;
        }
        try {
            return passwordEncoder.matches(rawPassword, encodedPassword);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}

