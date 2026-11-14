package com.example.javaweb.controller;

import com.example.javaweb.dto.response.CaptchaResponseDTO;
import com.example.javaweb.service.CaptchaService;
import com.example.javaweb.common.Result;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user/captcha")
public class CaptchaController {

    @Resource
    private CaptchaService captchaService;

    //获取验证码
    @GetMapping
    public Result<CaptchaResponseDTO> captcha() {
        return Result.success(captchaService.generate());
    }
}
