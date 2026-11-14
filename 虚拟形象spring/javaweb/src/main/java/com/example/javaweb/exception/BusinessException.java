package com.example.javaweb.exception;

import lombok.Getter;

/**
 * 业务异常
 * @author system
 */

@Getter
public class BusinessException extends RuntimeException {

        private final String code;
        private final Object data;

        public BusinessException(String message){
        super(message);
        this.code = "BUSINESS_ERROR";
        this.data = null;
    }

    public BusinessException(String code, String message){
        super(message);
        this.code = code;
        this.data = null;
    }


}
