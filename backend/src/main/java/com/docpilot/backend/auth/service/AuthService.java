package com.docpilot.backend.auth.service;

import com.docpilot.backend.auth.dto.LoginRequest;
import com.docpilot.backend.auth.dto.PasswordLoginRequest;
import com.docpilot.backend.auth.dto.RegisterRequest;
import com.docpilot.backend.auth.dto.SendCodeRequest;
import com.docpilot.backend.auth.vo.LoginResponse;
import com.docpilot.backend.auth.vo.SendCodeResponse;

public interface AuthService {

    SendCodeResponse sendCode(SendCodeRequest request);

    LoginResponse login(LoginRequest request);

    LoginResponse register(RegisterRequest request);

    LoginResponse loginByPassword(PasswordLoginRequest request);
}

