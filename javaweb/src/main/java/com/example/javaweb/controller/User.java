package com.example.javaweb.controller;

import com.example.javaweb.DTO.command.UserRegisterCommandDTO;
import com.example.javaweb.DTO.response.UserLoginResponseDTO;
import com.example.javaweb.Service.UserService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import com.example.javaweb.DTO.command.UserLoginCommandDTO;
import com.example.javaweb.common.Result;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/user")
public class User {
    @Resource
    private UserService userService;

    //登录
    @PostMapping("/login")
    public Result<UserLoginResponseDTO> login(@Valid @RequestBody UserLoginCommandDTO commandDTO){
        //校验参数
        System.out.println(commandDTO.getUsername());
        System.out.println(commandDTO.getPassword());
        //调用服务层方法
        Result<UserLoginResponseDTO> result = userService.login(commandDTO);
        return result;
    }

    ;//注册
    @PostMapping("/register")
    public Result<UserLoginResponseDTO.UserDetailResponseDTO>
    register(@Valid @RequestBody UserRegisterCommandDTO commandDTO){
        return userService.register(commandDTO);
    }

}
