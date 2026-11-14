package com.example.javaweb.controller;

import com.example.javaweb.dto.request.CreateTaskCommandDTO;
import com.example.javaweb.dto.request.RespondTaskCommandDTO;
import com.example.javaweb.dto.request.UpdateTaskCommandDTO;
import com.example.javaweb.dto.response.OccurrenceItemResponseDTO;
import com.example.javaweb.dto.response.TaskItemResponseDTO;
import com.example.javaweb.dto.response.TaskListResponseDTO;
import com.example.javaweb.dto.response.TaskResponseResultDTO;
import com.example.javaweb.service.TaskResponseService;
import com.example.javaweb.service.TaskService;
import com.example.javaweb.common.Result;
import com.example.javaweb.security.CurrentUserResolver;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 事务闹钟与完成度回应。
 */
@RestController
@RequestMapping("/api")
public class TaskController {

    @Resource
    private TaskService taskService;

    @Resource
    private TaskResponseService responseService;

    @Resource
    private CurrentUserResolver currentUserResolver;

    @PostMapping("/tasks")
    public Result<TaskItemResponseDTO> create(
            @Valid @RequestBody CreateTaskCommandDTO command,
            Authentication authentication) {
        Long userId = currentUserResolver.requireUserId(authentication);
        return Result.success(taskService.create(userId, command));
    }

    @GetMapping("/tasks")
    public Result<TaskListResponseDTO> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(defaultValue = "ACTIVE") String status,
            Authentication authentication) {
        Long userId = currentUserResolver.requireUserId(authentication);
        return Result.success(taskService.list(userId, page, size, status));
    }

    @GetMapping("/tasks/{id}")
    public Result<TaskItemResponseDTO> get(
            @PathVariable Long id,
            Authentication authentication) {
        Long userId = currentUserResolver.requireUserId(authentication);
        return Result.success(taskService.get(userId, id));
    }

    @PatchMapping("/tasks/{id}")
    public Result<TaskItemResponseDTO> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTaskCommandDTO command,
            Authentication authentication) {
        Long userId = currentUserResolver.requireUserId(authentication);
        return Result.success(taskService.update(userId, id, command));
    }

    @DeleteMapping("/tasks/{id}")
    public Result<Void> archive(
            @PathVariable Long id,
            Authentication authentication) {
        Long userId = currentUserResolver.requireUserId(authentication);
        taskService.archive(userId, id);
        return Result.success();
    }

    @GetMapping("/task-occurrences")
    public Result<List<OccurrenceItemResponseDTO>> occurrences(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "50") Integer limit,
            Authentication authentication) {
        Long userId = currentUserResolver.requireUserId(authentication);
        return Result.success(taskService.occurrences(userId, from, to, status, limit));
    }

    @PostMapping("/task-occurrences/{id}/response")
    public Result<TaskResponseResultDTO> respond(
            @PathVariable Long id,
            @Valid @RequestBody RespondTaskCommandDTO command,
            Authentication authentication) {
        Long userId = currentUserResolver.requireUserId(authentication);
        return Result.success(responseService.respond(userId, id, command));
    }
}
