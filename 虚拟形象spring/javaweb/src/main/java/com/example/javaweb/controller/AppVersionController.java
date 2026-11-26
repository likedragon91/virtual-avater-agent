package com.example.javaweb.controller;

import com.example.javaweb.common.Result;
import com.example.javaweb.dto.response.AppVersionResponseDTO;
import com.example.javaweb.service.AppVersionService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户侧检查更新：返回管理员发布的最新桌面端版本。
 */
@RestController
@RequestMapping("/api/app/version")
public class AppVersionController {

    @Resource
    private AppVersionService appVersionService;

    @GetMapping("/latest")
    public Result<AppVersionResponseDTO> latest() {
        return Result.success(appVersionService.latest());
    }
}
