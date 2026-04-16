package com.docpilot.backend.common.error;

public enum ErrorCode {
    SUCCESS(0, "成功"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未登录或登录已失效"),
    FORBIDDEN(403, "无权限访问"),
    NOT_FOUND(404, "资源不存在"),
    BUSINESS_ERROR(1000, "业务处理失败"),
    SMS_CODE_INVALID(1001, "验证码错误"),
    SMS_CODE_EXPIRED(1002, "验证码已过期"),
    USER_PHONE_EXISTS(1003, "手机号已存在"),
    FILE_TYPE_NOT_SUPPORTED(1004, "文件类型不支持"),
    FILE_UPLOAD_FAILED(1005, "文件上传失败"),
    FILE_RECORD_NOT_FOUND(1006, "文件记录不存在"),
    FILE_RECORD_FORBIDDEN(1007, "无权访问该文件记录"),
    DOCUMENT_CREATE_FAILED(1008, "文档创建失败"),
    DOCUMENT_NOT_FOUND(1009, "文档不存在"),
    DOCUMENT_FORBIDDEN(1010, "无权访问该文档"),
    PARSE_TASK_CREATE_FAILED(1011, "解析任务创建失败"),
    DOCUMENT_CONTENT_EMPTY(1012, "文档内容为空"),
    AI_CALL_FAILED(1013, "AI 调用失败"),
    RATE_LIMIT_EXCEEDED(1014, "请求过于频繁，请稍后再试"),
    PARSE_TASK_NOT_FOUND(1015, "解析任务不存在"),
    PARSE_TASK_RETRY_NOT_ALLOWED(1016, "当前任务状态不允许重试"),
    PARSE_TASK_LOCKED(1017, "解析任务处理中，请稍后重试"),
    PARSE_TASK_REPARSE_NOT_ALLOWED(1018, "当前任务状态不允许重新解析"),
    USERNAME_ALREADY_EXISTS(1019, "用户名已存在"),
    USERNAME_OR_PASSWORD_INVALID(1020, "用户名或密码错误"),
    INTERNAL_SERVER_ERROR(500, "服务器内部错误");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}

