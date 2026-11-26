package com.example.javaweb.controller;

import com.example.javaweb.common.Result;
import com.example.javaweb.dto.response.AvatarModelResponseDTO;
import com.example.javaweb.entity.AvatarModel;
import com.example.javaweb.security.CurrentUserResolver;
import com.example.javaweb.service.AvatarModelService;
import jakarta.annotation.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Live2D 模型包上传/列表/下载/删除。下载走 Authorization，前端取 ArrayBuffer 后
 * 交给 live2d-renderer 的 load()，模型内部资源由 zip 解包得到。
 */
@RestController
@RequestMapping("/api/avatar/live2d")
public class AvatarModelController {

    @Resource
    private AvatarModelService avatarModelService;

    @Resource
    private CurrentUserResolver currentUserResolver;

    @GetMapping
    public Result<List<AvatarModelResponseDTO>> list(Authentication authentication) {
        Long userId = currentUserResolver.requireUserId(authentication);
        return Result.success(avatarModelService.list(userId));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<AvatarModelResponseDTO> upload(
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        Long userId = currentUserResolver.requireUserId(authentication);
        return Result.success(avatarModelService.upload(userId, file));
    }

    @GetMapping("/{id}/file")
    public ResponseEntity<org.springframework.core.io.Resource> file(
            @PathVariable Long id,
            Authentication authentication) {
        Long userId = currentUserResolver.requireUserId(authentication);
        AvatarModel model = avatarModelService.requireOwned(userId, id);
        org.springframework.core.io.Resource resource = avatarModelService.file(userId, id);
        MediaType mediaType = MediaTypeFactory.getMediaType(model.getFilePath())
                .orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok()
                .contentType(mediaType)
                .cacheControl(CacheControl.noStore())
                .header("Content-Disposition", "inline")
                .body(resource);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(
            @PathVariable Long id,
            Authentication authentication) {
        Long userId = currentUserResolver.requireUserId(authentication);
        avatarModelService.delete(userId, id);
        return Result.success();
    }
}
