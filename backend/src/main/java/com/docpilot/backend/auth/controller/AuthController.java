package com.docpilot.backend.auth.controller;

import com.docpilot.backend.auth.dto.LoginRequest;
import com.docpilot.backend.auth.dto.PasswordLoginRequest;
import com.docpilot.backend.auth.dto.RegisterRequest;
import com.docpilot.backend.auth.dto.SendCodeRequest;
import com.docpilot.backend.auth.service.AuthService;
import com.docpilot.backend.auth.vo.LoginResponse;
import com.docpilot.backend.auth.vo.SendCodeResponse;
import com.docpilot.backend.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/code")
    public ApiResponse<SendCodeResponse> sendCode(@Valid @RequestBody SendCodeRequest request) {
        return ApiResponse.success(authService.sendCode(request));
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @PostMapping("/register")
    public ApiResponse<LoginResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.success(authService.register(request));
    }

    @PostMapping("/password/login")
    public ApiResponse<LoginResponse> loginByPassword(@Valid @RequestBody PasswordLoginRequest request) {
        return ApiResponse.success(authService.loginByPassword(request));
    }
}

