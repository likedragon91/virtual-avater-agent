package com.example.javaweb.controller;

import com.example.javaweb.common.Result;
import com.example.javaweb.dto.request.UpdateAvatarConfigCommandDTO;
import com.example.javaweb.dto.response.AvatarCatalogResponseDTO;
import com.example.javaweb.dto.response.AvatarConfigResponseDTO;
import com.example.javaweb.security.CurrentUserResolver;
import com.example.javaweb.service.AvatarConfigService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 形象目录与用户形象配置。用户身份从 JWT 注入。
 */
@RestController
@RequestMapping("/api/avatar")
public class AvatarController {

    @Resource
    private AvatarConfigService avatarConfigService;

    @Resource
    private CurrentUserResolver currentUserResolver;

    @GetMapping("/catalog")
    public Result<AvatarCatalogResponseDTO> catalog() {
        return Result.success(avatarConfigService.catalog());
    }

    @GetMapping("/config")
    public Result<AvatarConfigResponseDTO> config(Authentication authentication) {
        Long userId = currentUserResolver.requireUserId(authentication);
        return Result.success(avatarConfigService.get(userId));
    }

    @PutMapping("/config")
    public Result<AvatarConfigResponseDTO> update(
            @Valid @RequestBody UpdateAvatarConfigCommandDTO command,
            Authentication authentication) {
        Long userId = currentUserResolver.requireUserId(authentication);
        return Result.success(avatarConfigService.update(userId, command));
    }
}
