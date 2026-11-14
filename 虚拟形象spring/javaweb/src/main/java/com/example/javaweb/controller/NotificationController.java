package com.example.javaweb.controller;

import com.example.javaweb.dto.response.NotificationListResponseDTO;
import com.example.javaweb.service.NotificationService;
import com.example.javaweb.service.NotificationSseService;
import com.example.javaweb.common.Result;
import com.example.javaweb.security.CurrentUserResolver;
import jakarta.annotation.Resource;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 站内通知：列表、未读数、已读、SSE 实时推送。
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    @Resource
    private NotificationService notificationService;

    @Resource
    private NotificationSseService sseService;

    @Resource
    private CurrentUserResolver currentUserResolver;

    @GetMapping
    public Result<NotificationListResponseDTO> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            Authentication authentication) {
        Long userId = currentUserResolver.requireUserId(authentication);
        return Result.success(notificationService.list(userId, page, size));
    }

    @GetMapping("/unread-count")
    public Result<Long> unreadCount(Authentication authentication) {
        Long userId = currentUserResolver.requireUserId(authentication);
        return Result.success(notificationService.unreadCount(userId));
    }

    @PostMapping("/{id}/read")
    public Result<Void> markRead(
            @PathVariable Long id,
            Authentication authentication) {
        Long userId = currentUserResolver.requireUserId(authentication);
        notificationService.markRead(userId, id);
        return Result.success();
    }

    @PostMapping("/read-all")
    public Result<Void> markAllRead(Authentication authentication) {
        Long userId = currentUserResolver.requireUserId(authentication);
        notificationService.markAllRead(userId);
        return Result.success();
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(Authentication authentication) {
        // 必须在同步线程取 userId，异步执行时 SecurityContext 不可用
        Long userId = currentUserResolver.requireUserId(authentication);
        return sseService.subscribe(userId);
    }
}
