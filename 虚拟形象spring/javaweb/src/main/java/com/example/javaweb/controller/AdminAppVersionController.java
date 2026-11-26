package com.example.javaweb.controller;

import com.example.javaweb.common.Result;
import com.example.javaweb.dto.response.AppVersionResponseDTO;
import com.example.javaweb.security.CurrentUserResolver;
import com.example.javaweb.service.AppVersionService;
import jakarta.annotation.Resource;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
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
 * 管理员发布桌面端安装包。上传即发布，并默认向所有启用用户推送 APP_UPDATE 通知。
 */
@RestController
@RequestMapping("/api/admin/app-versions")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAppVersionController {

    @Resource
    private AppVersionService appVersionService;

    @Resource
    private CurrentUserResolver currentUserResolver;

    @GetMapping
    public Result<List<AppVersionResponseDTO>> list() {
        return Result.success(appVersionService.list());
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<AppVersionResponseDTO> publish(
            @RequestParam("version") String version,
            @RequestParam(value = "notes", required = false) String notes,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "notify", defaultValue = "true") boolean notify,
            Authentication authentication) {
        Long adminId = currentUserResolver.requireUserId(authentication);
        return Result.success(appVersionService.publish(adminId, version, notes, file, notify));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        appVersionService.delete(id);
        return Result.success();
    }
}
