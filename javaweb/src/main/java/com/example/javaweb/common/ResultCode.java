package com.example.javaweb.common;

public enum ResultCode {
    SUCCESS("200","操作成功"),
    ERROR("-1","操作失败"), PARAM_ERROR("-2","参数错误");


    private String code;
    private String msg;

    ResultCode(String code,String msg) {
        this.code = code;
        this.msg = msg;
    }

    public String getCode() {
        return code;
    }

    public String getMsg() {
        return msg;
    }

}
