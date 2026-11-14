package com.example.javaweb.controller;

import com.example.javaweb.dto.request.CreateSessionCommandDTO;
import com.example.javaweb.dto.request.SendMessageCommandDTO;
import com.example.javaweb.dto.request.UpdateSessionCommandDTO;
import com.example.javaweb.dto.response.ChatSessionResponseDTO;
import com.example.javaweb.dto.response.MessagePageResponseDTO;
import com.example.javaweb.dto.response.SessionListResponseDTO;
import com.example.javaweb.service.AgentChatService;
import com.example.javaweb.service.AgentSessionService;
import com.example.javaweb.common.Result;
import com.example.javaweb.entity.ChatSession;
import com.example.javaweb.security.CurrentUserResolver;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Agent 工作台会话接口。
 * 所有接口都需要登录；用户身份从 JWT 解析，不接受客户端传 userId。
 */
@RestController
@RequestMapping("/api/agent")
public class AgentSessionController {

    @Resource
    private AgentSessionService sessionService;

    @Resource
    private AgentChatService chatService;

    @Resource
    private CurrentUserResolver currentUserResolver;

    @PostMapping("/sessions")
    public Result<ChatSessionResponseDTO> create(
            @Valid @RequestBody(required = false) CreateSessionCommandDTO command,
            Authentication authentication) {
        Long userId = currentUserResolver.requireUserId(authentication);
        CreateSessionCommandDTO dto = command == null ? new CreateSessionCommandDTO() : command;
        ChatSession session = sessionService.create(userId, dto);
        return Result.success(sessionService.toSessionResponse(session));
    }

    @GetMapping("/sessions")
    public Result<SessionListResponseDTO> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            Authentication authentication) {
        Long userId = currentUserResolver.requireUserId(authentication);
        return Result.success(sessionService.list(userId, page, size));
    }

    @GetMapping("/sessions/{id}")
    public Result<ChatSessionResponseDTO> get(
            @PathVariable Long id,
            Authentication authentication) {
        Long userId = currentUserResolver.requireUserId(authentication);
        ChatSession session = sessionService.requireOwned(userId, id);
        return Result.success(sessionService.toSessionResponse(session));
    }

    @PatchMapping("/sessions/{id}")
    public Result<ChatSessionResponseDTO> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateSessionCommandDTO command,
            Authentication authentication) {
        Long userId = currentUserResolver.requireUserId(authentication);
        ChatSession session = sessionService.update(userId, id, command);
        return Result.success(sessionService.toSessionResponse(session));
    }

    @DeleteMapping("/sessions/{id}")
    public Result<Void> delete(
            @PathVariable Long id,
            Authentication authentication) {
        sessionService.delete(currentUserResolver.requireUserId(authentication), id);
        return Result.success();
    }

    @GetMapping("/sessions/{id}/messages")
    public Result<MessagePageResponseDTO> messages(
            @PathVariable Long id,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") long size,
            Authentication authentication) {
        Long userId = currentUserResolver.requireUserId(authentication);
        return Result.success(sessionService.messages(userId, id, cursor, size));
    }

    /**
     * 发送消息并流式返回。必须用 fetch + ReadableStream 接收，EventSource 不支持带 Authorization。
     */
    @PostMapping(value = "/sessions/{id}/messages/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @PathVariable Long id,
            @Valid @RequestBody SendMessageCommandDTO command,
            Authentication authentication) {
        // 关键：必须在同步线程里取 userId，异步执行时 SecurityContext 不可用
        Long userId = currentUserResolver.requireUserId(authentication);
        return chatService.stream(id, userId, command);
    }

    @PostMapping("/runs/{runId}/cancel")
    public Result<Void> cancel(
            @PathVariable Long runId,
            Authentication authentication) {
        chatService.cancel(currentUserResolver.requireUserId(authentication), runId);
        return Result.success();
    }
}
