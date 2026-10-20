package com.example.javaweb.common;

import lombok.Data;
import lombok.Getter;

@Data
public class Result<T> {
    private String code;
    private String msg;
    private T data;


    public static <T> Result<T> success( ){
        Result<T> result =new Result<>();
        result.setCode(ResultCode.SUCCESS.getCode());
        result.setMsg(ResultCode.SUCCESS.getMsg());
        return result;
    }



    //方法重载
    public static <T> Result<T> success(T data){
        Result<T> result =success();
        result.setData(data);
        return result;
    }

    public static <T> Result<T> error( String code,String msg,T data){
        Result<T> result =new Result<>();
        result.setCode(code);
        result.setMsg(msg);
        result.setData(data);
        return result;
    }

    public static <T> Result<T> error( String code,String msg){
        Result<T> result =new Result<>();
        result.setCode(code);
        result.setMsg(msg);
        return result;
    }

    public String getCode(){
        return code;
    }
    public String getMsg() {

        return msg;
    }
}
