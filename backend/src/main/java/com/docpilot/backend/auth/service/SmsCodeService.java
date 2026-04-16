package com.docpilot.backend.auth.service;

public interface SmsCodeService {

    String sendLoginCode(String phone);

    void verifyLoginCode(String phone, String code);
}

