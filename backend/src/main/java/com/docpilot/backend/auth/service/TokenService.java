package com.docpilot.backend.auth.service;

import com.docpilot.backend.user.entity.User;

public interface TokenService {

    String createLoginToken(User user);
}

