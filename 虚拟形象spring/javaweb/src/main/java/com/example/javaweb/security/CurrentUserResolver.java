package com.example.javaweb.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.javaweb.entity.User;
import com.example.javaweb.exception.BusinessException;
import com.example.javaweb.mapper.UserMapper;
import jakarta.annotation.Resource;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * 从 SecurityContext 解析当前用户 id。
 * JWT 过滤器会把用户 id 放进 LoginUser；旧 token/兜底场景按用户名查库。
 */
@Component
public class CurrentUserResolver {

    @Resource
    private UserMapper userMapper;

    public Long requireUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new BusinessException("UNAUTHORIZED", "请先登录");
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof LoginUser loginUser && loginUser.getUserId() != null) {
            return loginUser.getUserId();
        }

        String username = authentication.getName();
        if (username != null && !username.isBlank()) {
            User found = userMapper.selectOne(new LambdaQueryWrapper<User>()
                    .eq(User::getUsername, username));
            if (found != null && found.getId() != null) {
                return found.getId();
            }
        }
        throw new BusinessException("UNAUTHORIZED", "登录状态无效，请重新登录");
    }
}
