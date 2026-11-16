package com.example.javaweb.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.javaweb.dto.request.CreateSessionCommandDTO;
import com.example.javaweb.dto.request.UpdateSessionCommandDTO;
import com.example.javaweb.dto.response.ChatMessageResponseDTO;
import com.example.javaweb.dto.response.ChatSessionResponseDTO;
import com.example.javaweb.dto.response.MessagePageResponseDTO;
import com.example.javaweb.dto.response.SessionListResponseDTO;
import com.example.javaweb.entity.ChatMessage;
import com.example.javaweb.entity.ChatSession;
import com.example.javaweb.exception.BusinessException;
import com.example.javaweb.mapper.ChatMessageMapper;
import com.example.javaweb.mapper.ChatSessionMapper;
import com.example.javaweb.persona.PersonaCatalog;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 会话与消息的查询/管理。所有公开方法都必须带 userId 做归属校验。
 */
@Service
public class AgentSessionService {
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Resource
    private ChatSessionMapper sessionMapper;

    @Resource
    private ChatMessageMapper messageMapper;

    public ChatSession create(Long userId, CreateSessionCommandDTO dto) {
        ChatSession session = new ChatSession();
        session.setUserId(userId);
        session.setTitle(hasText(dto.getTitle()) ? dto.getTitle().trim() : "新的对话");
        String personaCode = hasText(dto.getPersonaCode())
                ? dto.getPersonaCode().trim() : PersonaCatalog.SESSION_DEFAULT_CODE;
        if (!PersonaCatalog.SESSION_DEFAULT_CODE.equals(personaCode)
                && !PersonaCatalog.isValid(personaCode)) {
            throw new BusinessException("PARAM_ERROR", "不支持的人格");
        }
        session.setPersonaCode(personaCode);
        session.setStatus(1);
        session.setMessageCount(0);
        session.setLastActiveAt(LocalDateTime.now());
        session.setCreatedAt(LocalDateTime.now());
        sessionMapper.insert(session);
        return session;
    }

    public SessionListResponseDTO list(Long userId, long page, long size) {
        long currentPage = Math.max(page, 1);
        long pageSize = Math.min(Math.max(size, 1), 50);
        long offset = (currentPage - 1) * pageSize;

        LambdaQueryWrapper<ChatSession> countQuery = new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getUserId, userId)
                .eq(ChatSession::getStatus, 1);
        Long total = sessionMapper.selectCount(countQuery);

        LambdaQueryWrapper<ChatSession> listQuery = new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getUserId, userId)
                .eq(ChatSession::getStatus, 1)
                .orderByDesc(ChatSession::getLastActiveAt)
                .last("LIMIT " + pageSize + " OFFSET " + offset);
        List<ChatSession> rows = sessionMapper.selectList(listQuery);

        SessionListResponseDTO result = new SessionListResponseDTO();
        result.setRecords(rows.stream().map(this::toSessionResponse).collect(Collectors.toList()));
        result.setTotal(total == null ? 0 : total);
        result.setPage(currentPage);
        result.setSize(pageSize);
        return result;
    }

    public ChatSession requireOwned(Long userId, Long sessionId) {
        if (sessionId == null) {
            throw new BusinessException("PARAM_ERROR", "缺少会话 id");
        }
        ChatSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException("NOT_FOUND", "会话不存在");
        }
        if (!session.getUserId().equals(userId)) {
            throw new BusinessException("FORBIDDEN", "没有访问该会话的权限");
        }
        return session;
    }

    public ChatSession update(Long userId, Long sessionId, UpdateSessionCommandDTO dto) {
        ChatSession session = requireOwned(userId, sessionId);
        LambdaUpdateWrapper<ChatSession> update = new LambdaUpdateWrapper<ChatSession>()
                .eq(ChatSession::getId, sessionId)
                .eq(ChatSession::getUserId, userId);
        boolean changed = false;

        if (dto.getTitle() != null) {
            String title = dto.getTitle().trim();
            if (title.isEmpty()) {
                throw new BusinessException("PARAM_ERROR", "标题不能为空");
            }
            // 只更新标题，避免把并发的 message_count / last_active_at 覆盖回旧值
            update.set(ChatSession::getTitle, title);
            session.setTitle(title);
            changed = true;
        }
        if (dto.getStatus() != null) {
            update.set(ChatSession::getStatus, dto.getStatus());
            session.setStatus(dto.getStatus());
            changed = true;
        }
        if (dto.getPersonaCode() != null) {
            String personaCode = dto.getPersonaCode().trim();
            if (!PersonaCatalog.SESSION_DEFAULT_CODE.equals(personaCode)
                    && !PersonaCatalog.isValid(personaCode)) {
                throw new BusinessException("PARAM_ERROR", "不支持的人格");
            }
            update.set(ChatSession::getPersonaCode, personaCode);
            session.setPersonaCode(personaCode);
            changed = true;
        }
        if (!changed) {
            throw new BusinessException("PARAM_ERROR", "没有需要修改的内容");
        }
        sessionMapper.update(null, update);
        return session;
    }

    public void delete(Long userId, Long sessionId) {
        requireOwned(userId, sessionId);
        // chat_message / tool_call_log 由外键级联删除
        sessionMapper.deleteById(sessionId);
    }

    public MessagePageResponseDTO messages(Long userId, Long sessionId, Long cursor, long size) {
        requireOwned(userId, sessionId);
        long pageSize = Math.min(Math.max(size, 1), 50);
        Long safeCursor = cursor != null && cursor > 0 ? cursor : null;

        LambdaQueryWrapper<ChatMessage> query = new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, sessionId)
                .eq(ChatMessage::getUserId, userId)
                // 1-完成、2-失败、3-已取消都返回；0-生成中不返回，避免刷新时看到半成品
                .in(ChatMessage::getStatus, List.of(1, 2, 3))
                .in(ChatMessage::getSenderRole, List.of("user", "assistant"))
                .lt(safeCursor != null, ChatMessage::getId, safeCursor)
                .orderByDesc(ChatMessage::getId)
                .last("LIMIT " + (pageSize + 1));
        List<ChatMessage> rows = messageMapper.selectList(query);

        boolean hasMore = rows.size() > pageSize;
        if (hasMore) {
            rows = rows.subList(0, (int) pageSize);
        }
        Long nextCursor = rows.isEmpty() ? null : rows.get(rows.size() - 1).getId();
        Collections.reverse(rows);

        MessagePageResponseDTO result = new MessagePageResponseDTO();
        result.setRecords(rows.stream().map(this::toMessageResponse).collect(Collectors.toList()));
        result.setNextCursor(nextCursor);
        result.setHasMore(hasMore);
        return result;
    }

    public void touch(Long sessionId, Long userId, int delta) {
        sessionMapper.touch(sessionId, userId, delta);
    }

    public ChatSessionResponseDTO toSessionResponse(ChatSession session) {
        ChatSessionResponseDTO dto = new ChatSessionResponseDTO();
        dto.setId(session.getId());
        dto.setTitle(session.getTitle());
        dto.setPersonaCode(session.getPersonaCode());
        dto.setStatus(session.getStatus());
        dto.setMessageCount(session.getMessageCount());
        dto.setLastActiveAt(format(session.getLastActiveAt()));
        dto.setCreatedAt(format(session.getCreatedAt()));
        return dto;
    }

    public ChatMessageResponseDTO toMessageResponse(ChatMessage message) {
        ChatMessageResponseDTO dto = new ChatMessageResponseDTO();
        dto.setId(message.getId());
        dto.setSenderRole(message.getSenderRole());
        dto.setContent(message.getContent());
        dto.setContentType(message.getContentType());
        dto.setStatus(message.getStatus());
        dto.setTokens(message.getTokens());
        dto.setModel(message.getModel());
        dto.setCreatedAt(format(message.getCreatedAt()));
        return dto;
    }

    private static String format(LocalDateTime time) {
        return time == null ? null : time.format(DATE_TIME_FORMATTER);
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
