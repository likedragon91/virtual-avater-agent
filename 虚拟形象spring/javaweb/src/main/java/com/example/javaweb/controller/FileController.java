package com.example.javaweb.controller;

import com.example.javaweb.exception.BusinessException;
import com.example.javaweb.service.FileStorageService;
import jakarta.annotation.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Set;

/**
 * 公开头像图片。文件名为 UUID，路径经过白名单校验；
 * 该路径在 SecurityConfig 中放行，便于 img 标签直接加载。
 */
@RestController
@RequestMapping("/api/files")
public class FileController {
    private static final Set<String> ALLOWED_TYPES =
            Set.of("avatar", "ai-avatar", "avatar-image", "updates");
    private static final String SAFE_FILENAME = "^[a-zA-Z0-9_-]+\\.[a-z0-9]+$";

    @Resource
    private FileStorageService fileStorageService;

    @GetMapping("/{type}/{filename}")
    public ResponseEntity<org.springframework.core.io.Resource> file(@PathVariable String type, @PathVariable String filename) {
        if (!ALLOWED_TYPES.contains(type) || !filename.matches(SAFE_FILENAME)) {
            throw new BusinessException("NOT_FOUND", "文件不存在");
        }
        org.springframework.core.io.Resource resource = fileStorageService.load(type + "/" + filename);
        MediaType mediaType = MediaTypeFactory.getMediaType(filename)
                .orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok()
                .contentType(mediaType)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic())
                .header("X-Content-Type-Options", "nosniff")
                .body(resource);
    }
}
