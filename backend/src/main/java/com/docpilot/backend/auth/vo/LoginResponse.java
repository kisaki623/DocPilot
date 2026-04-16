package com.docpilot.backend.auth.vo;

public class LoginResponse {

    private String token;
    private Long userId;
    private String username;
    private String phone;
    private String nickname;

    public LoginResponse(String token, Long userId, String phone, String nickname) {
        this(token, userId, null, phone, nickname);
    }

    public LoginResponse(String token, Long userId, String username, String phone, String nickname) {
        this.token = token;
        this.userId = userId;
        this.username = username;
        this.phone = phone;
        this.nickname = nickname;
    }

    public String getToken() {
        return token;
    }

    public Long getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getPhone() {
        return phone;
    }

    public String getNickname() {
        return nickname;
    }
}

