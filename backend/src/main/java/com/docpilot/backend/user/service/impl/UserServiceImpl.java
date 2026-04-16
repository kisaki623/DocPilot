package com.docpilot.backend.user.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.docpilot.backend.common.error.ErrorCode;
import com.docpilot.backend.common.exception.BusinessException;
import com.docpilot.backend.common.util.ValidationUtils;
import com.docpilot.backend.user.entity.User;
import com.docpilot.backend.user.mapper.UserMapper;
import com.docpilot.backend.user.service.UserService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public User findById(Long userId) {
        ValidationUtils.requireNonNull(userId, "userId");
        return baseMapper.selectById(userId);
    }

    @Override
    public User findByPhone(String phone) {
        ValidationUtils.requireNonBlank(phone, "phone");
        return baseMapper.selectByPhone(phone);
    }

    @Override
    public User findByUsername(String username) {
        ValidationUtils.requireNonBlank(username, "username");
        return baseMapper.selectByUsername(username);
    }

    @Override
    public User registerByUsername(String username, String passwordHash, String nickname) {
        ValidationUtils.requireNonBlank(username, "username");
        ValidationUtils.requireNonBlank(passwordHash, "passwordHash");
        if (baseMapper.selectByUsername(username) != null) {
            throw new BusinessException(ErrorCode.USERNAME_ALREADY_EXISTS);
        }

        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordHash);
        user.setNickname((nickname == null || nickname.trim().isEmpty()) ? username : nickname.trim());
        user.setPhone(null);
        user.setStatus("ACTIVE");
        try {
            baseMapper.insert(user);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.USERNAME_ALREADY_EXISTS);
        }
        return user;
    }

    @Override
    public User createMinimalUserByPhone(String phone) {
        ValidationUtils.requireNonBlank(phone, "phone");
        if (baseMapper.selectByPhone(phone) != null) {
            throw new BusinessException(ErrorCode.USER_PHONE_EXISTS);
        }

        User user = new User();
        user.setPhone(phone);
        user.setUsername("u" + phone.substring(phone.length() - 4) + System.currentTimeMillis());
        user.setNickname("user-" + phone.substring(phone.length() - 4));
        user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString().replace("-", "")));
        user.setStatus("ACTIVE");
        baseMapper.insert(user);
        return user;
    }
}



