package com.docpilot.backend.auth.controller;

import com.docpilot.backend.common.api.ApiResponse;
import com.docpilot.backend.common.context.UserHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/protected")
public class AuthProtectedTestController {

    @GetMapping("/ping")
    public ApiResponse<String> ping() {
        Long userId = UserHolder.requireUserId();
        return ApiResponse.success("pong-user-" + userId);
    }
}

