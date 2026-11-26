package com.example.javaweb.controller;

import com.example.javaweb.common.Result;
import com.example.javaweb.dto.response.AvatarModelResponseDTO;
import com.example.javaweb.security.CurrentUserResolver;
import com.example.javaweb.service.AvatarModelService;
import jakarta.annotation.Resource;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 单图静态立绘上传。它不是 Live2D 模型（缺少 moc3），
 * 只作为 driver=image 的舞台形象使用。
 */
@RestController
@RequestMapping("/api/avatar/image")
public class AvatarImageController {

    @Resource
    private AvatarModelService avatarModelService;

    @Resource
    private CurrentUserResolver currentUserResolver;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<AvatarModelResponseDTO> upload(
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        Long userId = currentUserResolver.requireUserId(authentication);
        return Result.success(avatarModelService.uploadImage(userId, file));
    }
}
