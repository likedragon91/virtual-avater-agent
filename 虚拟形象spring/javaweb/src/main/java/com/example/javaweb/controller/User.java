package com.example.javaweb.controller;

import com.example.javaweb.DTO.command.UserRegisterCommandDTO;
import com.example.javaweb.DTO.response.OnlineUserResponseDTO;
import com.example.javaweb.DTO.response.UserListResponseDTO;
import com.example.javaweb.DTO.response.UserLoginResponseDTO;
import com.example.javaweb.Service.UserService;
import com.example.javaweb.config.JWTConfig;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import com.example.javaweb.DTO.command.UserLoginCommandDTO;
import com.example.javaweb.common.Result;
import com.example.javaweb.common.ResultCode;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("api/user")
public class User {
    @Resource
    private UserService userService;

    @Resource
    private JWTConfig jwtConfig;

    //登录
    @PostMapping("/login")
    public Result<UserLoginResponseDTO> login(@Valid @RequestBody UserLoginCommandDTO commandDTO){
        //调用服务层方法
        return userService.login(commandDTO);
    }

    //注册
    @PostMapping("/register")
    public Result<UserLoginResponseDTO.UserDetailResponseDTO>
    register(@Valid @RequestBody UserRegisterCommandDTO commandDTO){
        return userService.register(commandDTO);
    }

    //获取用户信息：优先按 query 中的 username 查询，未传则取当前登录用户
    @GetMapping("/message")
    public Result<UserLoginResponseDTO.UserDetailResponseDTO> message(
            @RequestParam(required = false) String username,
            Authentication authentication){
        //query 里传了 username 就按这个用户名查
        if (username != null && !username.isBlank()) {
            return userService.getMessage(username.trim());
        }
        //未登录（匿名用户）时返回统一错误，避免把 anonymousUser 当用户名查库
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            return Result.error(ResultCode.UNAUTHORIZED.getCode(), ResultCode.UNAUTHORIZED.getMsg());
        }
        //未传 username 时，返回当前登录用户信息
        return userService.getMessage(authentication.getName());
    }

    //用户列表（分页，仅管理员可访问）
    @GetMapping("/list")
    public Result<UserListResponseDTO> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String keyword){
        return userService.listUsers(page, size, keyword);
    }

    //在线用户列表（仅管理员可访问）
    @GetMapping("/online")
    public Result<List<OnlineUserResponseDTO>> online(){
        return userService.listOnlineUsers();
    }

    //退出登录：让当前 token 立即从在线列表中移除
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request){
        String header = request.getHeader(jwtConfig.getHeader());
        if (header != null && header.startsWith(jwtConfig.getTokenPrefix())) {
            userService.logout(header.substring(jwtConfig.getTokenPrefix().length()).trim());
        }
        return Result.success();
    }

}
