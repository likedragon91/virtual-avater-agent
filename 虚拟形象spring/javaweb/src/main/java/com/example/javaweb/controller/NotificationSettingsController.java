package com.example.javaweb.controller;

import com.example.javaweb.dto.request.UpdateNotificationSettingsCommandDTO;
import com.example.javaweb.dto.response.NotificationSettingsResponseDTO;
import com.example.javaweb.service.NotificationSettingsService;
import com.example.javaweb.common.Result;
import com.example.javaweb.security.CurrentUserResolver;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 通知设置：免打扰窗口、主动消息开关。
 */
@RestController
@RequestMapping("/api/notifications/settings")
public class NotificationSettingsController {

    @Resource
    private NotificationSettingsService settingsService;

    @Resource
    private CurrentUserResolver currentUserResolver;

    @GetMapping
    public Result<NotificationSettingsResponseDTO> get(Authentication authentication) {
        Long userId = currentUserResolver.requireUserId(authentication);
        return Result.success(settingsService.getSettings(userId));
    }

    @PutMapping
    public Result<NotificationSettingsResponseDTO> save(
            @Valid @RequestBody UpdateNotificationSettingsCommandDTO command,
            Authentication authentication) {
        Long userId = currentUserResolver.requireUserId(authentication);
        return Result.success(settingsService.save(userId, command));
    }
}
