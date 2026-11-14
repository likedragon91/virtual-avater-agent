package com.example.javaweb.controller;

import com.example.javaweb.dto.response.IntimacyLogListResponseDTO;
import com.example.javaweb.dto.response.RelationshipResponseDTO;
import com.example.javaweb.service.IntimacyService;
import com.example.javaweb.common.Result;
import com.example.javaweb.security.CurrentUserResolver;
import jakarta.annotation.Resource;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 亲密度、阶段与成长流水。
 */
@RestController
@RequestMapping("/api/relationship")
public class RelationshipController {

    @Resource
    private IntimacyService intimacyService;

    @Resource
    private CurrentUserResolver currentUserResolver;

    @GetMapping
    public Result<RelationshipResponseDTO> current(Authentication authentication) {
        Long userId = currentUserResolver.requireUserId(authentication);
        return Result.success(intimacyService.getRelationship(userId));
    }

    @GetMapping("/logs")
    public Result<IntimacyLogListResponseDTO> logs(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            Authentication authentication) {
        Long userId = currentUserResolver.requireUserId(authentication);
        return Result.success(intimacyService.getLogs(userId, page, size));
    }
}
