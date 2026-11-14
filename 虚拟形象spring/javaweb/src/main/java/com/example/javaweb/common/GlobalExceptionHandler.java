package com.example.javaweb.common;


import com.example.javaweb.exception.BusinessException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice


public class GlobalExceptionHandler {
    //处理参数校验异常
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<String> handleException(MethodArgumentNotValidException e){
        //处理异常数据
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(","));
        return Result.error(ResultCode.PARAM_ERROR.getCode(),ResultCode.PARAM_ERROR.getMsg(),message);
    }

    //处理业务异常
    @ExceptionHandler(BusinessException.class)
    public Result<Object> handleBusinessException(BusinessException e){
        //如果异常携带额外数据
        if (e.getData() != null){
            return Result.error(e.getCode(),e.getMessage(),e.getData());
        }
        return Result.error(e.getCode(),e.getMessage(),null);
    }
}
