package com.docpilot.backend.user.service;

import com.docpilot.backend.common.exception.BusinessException;
import com.docpilot.backend.common.error.ErrorCode;
import com.docpilot.backend.user.entity.User;
import com.docpilot.backend.user.mapper.UserMapper;
import com.docpilot.backend.user.service.impl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        userService = new UserServiceImpl(passwordEncoder);
        ReflectionTestUtils.setField(userService, "baseMapper", userMapper);
    }

    @Test
    void shouldFindUserById() {
        User user = new User();
        user.setId(1L);
        user.setUsername("demo");
        when(userMapper.selectById(1L)).thenReturn(user);

        User result = userService.findById(1L);

        assertEquals(1L, result.getId());
        assertEquals("demo", result.getUsername());
        verify(userMapper).selectById(1L);
    }

    @Test
    void shouldFindUserByPhone() {
        User user = new User();
        user.setPhone("13800000000");
        when(userMapper.selectByPhone("13800000000")).thenReturn(user);

        User result = userService.findByPhone("13800000000");

        assertEquals("13800000000", result.getPhone());
        verify(userMapper).selectByPhone("13800000000");
    }

    @Test
    void shouldThrowWhenPhoneIsBlank() {
        BusinessException ex = assertThrows(BusinessException.class, () -> userService.findByPhone("  "));

        assertTrue(ex.getMessage().contains("phone"));
    }

    @Test
    void shouldFindUserByUsername() {
        User user = new User();
        user.setUsername("alice_demo");
        when(userMapper.selectByUsername("alice_demo")).thenReturn(user);

        User result = userService.findByUsername("alice_demo");

        assertEquals("alice_demo", result.getUsername());
        verify(userMapper).selectByUsername("alice_demo");
    }

    @Test
    void shouldRegisterByUsername() {
        when(userMapper.selectByUsername("alice_demo")).thenReturn(null);
        when(userMapper.insert(any(User.class))).thenAnswer(invocation -> {
            User inserted = invocation.getArgument(0);
            inserted.setId(100L);
            return 1;
        });

        User created = userService.registerByUsername("alice_demo", "$2a$10$hash", "Alice");

        assertEquals(100L, created.getId());
        assertEquals("alice_demo", created.getUsername());
        assertEquals("Alice", created.getNickname());
    }

    @Test
    void shouldRejectDuplicateUsernameOnRegister() {
        User existing = new User();
        existing.setUsername("alice_demo");
        when(userMapper.selectByUsername("alice_demo")).thenReturn(existing);

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> userService.registerByUsername("alice_demo", "$2a$10$hash", "Alice")
        );

        assertEquals(ErrorCode.USERNAME_ALREADY_EXISTS, ex.getErrorCode());
    }
}

