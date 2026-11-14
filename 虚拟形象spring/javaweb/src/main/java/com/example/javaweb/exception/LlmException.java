package com.example.javaweb.exception;

import lombok.Getter;

/**
 * 模型调用异常。code 用于 SSE error 事件，msg 是用户可读文案。
 */
@Getter
public class LlmException extends RuntimeException {
    private final String code;

    public LlmException(String code, String message) {
        super(message);
        this.code = code;
    }

    public LlmException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
}
