package com.example.javaweb.service;

import com.example.javaweb.dto.response.NotificationResponseDTO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalTime;

/**
 * 在线推送。免打扰时段只写入站内信，不发 SSE / Web Push。
 */
@Service
public class NotificationPushService {

    @Resource
    private NotificationSseService sseService;

    @Resource
    private NotificationSettingsService settingsService;

    @Resource
    private WebPushService webPushService;

    public void push(Long userId, NotificationResponseDTO notification) {
        if (notification == null) {
            return;
        }
        if (settingsService.isDnd(userId, LocalTime.now())) {
            return;
        }
        sseService.push(userId, notification);
        webPushService.sendToUser(userId, notification);
    }

    /**
     * 系统级通知：不遵循免打扰。用于管理员发布的版本更新等需要立即提示的消息。
     */
    public void pushSystem(Long userId, NotificationResponseDTO notification) {
        if (notification == null) {
            return;
        }
        sseService.push(userId, notification);
        webPushService.sendToUser(userId, notification);
    }
}
