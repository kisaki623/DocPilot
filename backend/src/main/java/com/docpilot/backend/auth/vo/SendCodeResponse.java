package com.docpilot.backend.auth.vo;

public class SendCodeResponse {

    private String phone;
    private String devCode;

    public SendCodeResponse(String phone, String devCode) {
        this.phone = phone;
        this.devCode = devCode;
    }

    public String getPhone() {
        return phone;
    }

    public String getDevCode() {
        return devCode;
    }
}

