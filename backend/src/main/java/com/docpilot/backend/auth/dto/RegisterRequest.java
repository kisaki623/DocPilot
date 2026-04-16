package com.docpilot.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class RegisterRequest {

    @NotBlank(message = "username must not be blank")
    @Pattern(regexp = "^[A-Za-z0-9_.-]{4,32}$", message = "username format is invalid")
    private String username;

    @NotBlank(message = "password must not be blank")
    @Size(min = 8, max = 64, message = "password length must be between 8 and 64")
    private String password;

    @Size(max = 64, message = "nickname length must be less than or equal to 64")
    private String nickname;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }
}

