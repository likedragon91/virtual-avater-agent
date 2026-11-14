package com.example.javaweb.controller;

import com.example.javaweb.dto.request.SubscribePushCommandDTO;
import com.example.javaweb.dto.request.UnsubscribePushCommandDTO;
import com.example.javaweb.dto.response.PushPublicKeyResponseDTO;
import com.example.javaweb.service.WebPushService;
import com.example.javaweb.common.Result;
import com.example.javaweb.security.CurrentUserResolver;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Web Push 订阅。
 */
@RestController
@RequestMapping("/api/notifications/push")
public class PushSubscriptionController {

    @Resource
    private WebPushService webPushService;

    @Resource
    private CurrentUserResolver currentUserResolver;

    @GetMapping("/public-key")
    public Result<PushPublicKeyResponseDTO> publicKey() {
        return Result.success(webPushService.publicKey());
    }

    @PostMapping("/subscribe")
    public Result<Void> subscribe(
            @Valid @RequestBody SubscribePushCommandDTO command,
            Authentication authentication) {
        Long userId = currentUserResolver.requireUserId(authentication);
        webPushService.subscribe(userId, command);
        return Result.success();
    }

    @PostMapping("/unsubscribe")
    public Result<Void> unsubscribe(
            @Valid @RequestBody UnsubscribePushCommandDTO command,
            Authentication authentication) {
        Long userId = currentUserResolver.requireUserId(authentication);
        webPushService.unsubscribe(userId, command.getEndpoint());
        return Result.success();
    }

    @PostMapping("/test")
    public Result<Integer> test(Authentication authentication) {
        Long userId = currentUserResolver.requireUserId(authentication);
        return Result.success(webPushService.sendTest(userId));
    }
}
