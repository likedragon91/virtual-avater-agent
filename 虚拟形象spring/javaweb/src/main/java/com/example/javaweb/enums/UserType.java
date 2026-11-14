package com.example.javaweb.enums;

import lombok.Getter;

@Getter
public enum UserType {
    NORMAL(1, "普通用户"),
    ADMIN(2, "管理员"),
    VIP(3, "VIP");

    private final Integer code;
    private final String description;

    UserType(Integer code, String description) {
        this.code = code;
        this.description = description;
    }

    //根据代码获取枚举，找不到返回 null
    public static UserType fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (UserType type : UserType.values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        return null;
    }

    //校验代码是否合法
    public static boolean isValid(Integer code) {
        return fromCode(code) != null;
    }
}
