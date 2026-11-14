package com.example.javaweb.security;

import lombok.Getter;

import java.security.Principal;

/**
 * 放入 SecurityContext 的登录主体。
 * 实现 Principal 并让 getName() 返回用户名，兼容现有 authentication.getName() 的用法。
 */
@Getter
public class LoginUser implements Principal {
    private final Long userId;
    private final String username;
    private final Integer roleType;

    public LoginUser(Long userId, String username, Integer roleType) {
        this.userId = userId;
        this.username = username;
        this.roleType = roleType;
    }

    @Override
    public String getName() {
        return username;
    }
}
