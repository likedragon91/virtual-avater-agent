package com.example.javaweb.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.javaweb.config.AgentProperties;
import com.example.javaweb.entity.ChatMessage;
import com.example.javaweb.entity.ChatSession;
import com.example.javaweb.mapper.ChatMessageMapper;
import com.example.javaweb.mapper.ChatSessionMapper;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * 滚动会话摘要：只处理 summary_message_id 之后的消息，异步执行，失败不影响聊天。
 */
@Service
public class ConversationSummaryService {
    private static final Logger log = LoggerFactory.getLogger(ConversationSummaryService.class);

    private static final String SYSTEM_PROMPT = """
            你负责把陪伴对话压缩成可复用的会话摘要。
            保留用户明确说过的稳定信息、重要事件、未完成的话题和情绪基调。
            不要编造，不要做心理诊断，不要加入建议。用第三人称陈述，控制在 400 字以内。
            """;

    @Resource
    private ChatMessageMapper messageMapper;

    @Resource
    private ChatSessionMapper sessionMapper;

    @Resource
    private DeepSeekClient deepSeekClient;

    @Resource
    private AgentProperties agentProperties;

    @Resource(name = "summaryExecutor")
    private Executor summaryExecutor;

    private final Set<Long> running = ConcurrentHashMap.newKeySet();

    public void summarizeAsync(Long sessionId) {
        if (sessionId == null) {
            return;
        }
        try {
            summaryExecutor.execute(() -> summarize(sessionId));
        } catch (Exception e) {
            log.warn("submit summary task failed, sessionId={}", sessionId, e);
        }
    }

    void summarize(Long sessionId) {
        if (!running.add(sessionId)) {
            return;
        }
        try {
            ChatSession session = sessionMapper.selectById(sessionId);
            if (session == null) {
                return;
            }
            AgentProperties.Context config = agentProperties.getContext();
            Long lastSummarizedId = session.getSummaryMessageId() == null
                    ? 0L : session.getSummaryMessageId();

            List<ChatMessage> rows = messageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                    .eq(ChatMessage::getSessionId, sessionId)
                    .eq(ChatMessage::getStatus, 1)
                    .in(ChatMessage::getSenderRole, List.of("user", "assistant"))
                    .gt(ChatMessage::getId, lastSummarizedId)
                    .orderByAsc(ChatMessage::getId)
                    .last("LIMIT 60"));
            if (rows.size() < Math.max(config.getSummaryTriggerMessages(), 2)) {
                return;
            }

            String transcript = buildTranscript(rows);
            if (transcript.isBlank()) {
                return;
            }
            String summary = deepSeekClient.complete(List.of(
                    Map.of("role", "system", "content", SYSTEM_PROMPT),
                    Map.of("role", "user", "content",
                            buildInput(session.getSummary(), transcript, config.getSummaryMaxChars()))),
                    config.getSummaryMaxTokens(),
                    "summary-" + sessionId);
            if (summary == null || summary.isBlank()) {
                return;
            }

            Long lastMessageId = rows.get(rows.size() - 1).getId();
            sessionMapper.updateSummary(sessionId, summary.trim(), lastMessageId, LocalDateTime.now());
            log.info("conversation summary updated, sessionId={}, until={}", sessionId, lastMessageId);
        } catch (Exception e) {
            log.warn("conversation summary failed, sessionId={}", sessionId, e);
        } finally {
            running.remove(sessionId);
        }
    }

    private static String buildInput(String previousSummary, String transcript, int maxChars) {
        StringBuilder input = new StringBuilder();
        if (previousSummary != null && !previousSummary.isBlank()) {
            input.append("已有摘要：\n").append(truncate(previousSummary.trim(), maxChars)).append("\n\n");
        }
        input.append("新增对话：\n").append(transcript);
        input.append("\n\n请输出更新后的会话摘要。");
        return input.toString();
    }

    private static String buildTranscript(List<ChatMessage> messages) {
        StringBuilder transcript = new StringBuilder();
        for (ChatMessage message : messages) {
            if (message.getContent() == null || message.getContent().isBlank()) {
                continue;
            }
            String role = "assistant".equals(message.getSenderRole()) ? "助手" : "用户";
            transcript.append(role).append("：")
                    .append(truncate(message.getContent().trim(), 500))
                    .append("\n");
        }
        return transcript.toString();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max) + "...";
    }
}
