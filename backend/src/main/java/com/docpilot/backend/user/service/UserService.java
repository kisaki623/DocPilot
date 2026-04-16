package com.docpilot.backend.user.service;

import com.docpilot.backend.user.entity.User;

public interface UserService {

    User findById(Long userId);

    User findByPhone(String phone);

    User findByUsername(String username);

    User registerByUsername(String username, String passwordHash, String nickname);

    User createMinimalUserByPhone(String phone);
}


