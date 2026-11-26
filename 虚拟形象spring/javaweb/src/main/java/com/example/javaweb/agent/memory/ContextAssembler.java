package com.example.javaweb.agent.memory;

import com.example.javaweb.config.AgentProperties;
import com.example.javaweb.dto.projection.MemoryRow;
import com.example.javaweb.entity.ChatSession;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 当前上下文预算：摘要 + 检索到的记忆 + 最近历史，三者共享字符预算。
 * 摘要负责更早的对话，历史窗口只保留最近部分，避免重复注入。
 */
@Component
public class ContextAssembler {

    private static final int MIN_HISTORY_CHARS = 1000;

    @Resource
    private MemoryRetriever memoryRetriever;

    @Resource
    private AgentProperties agentProperties;

    public record Context(List<MemoryRow> memories, String summary, int historyMaxChars) {
    }

    public Context assemble(Long userId, ChatSession session, String latestMessage) {
        AgentProperties.Context config = agentProperties.getContext();
        List<MemoryRow> memories = memoryRetriever.retrieve(userId, latestMessage);

        int memoryChars = 0;
        for (MemoryRow memory : memories) {
            memoryChars += memory.getContent() == null ? 0 : memory.getContent().length();
        }

        String summary = truncate(session == null ? null : session.getSummary(),
                config.getSummaryMaxChars());
        int summaryChars = summary == null ? 0 : summary.length();

        int historyMaxChars = Math.max(
                config.getMaxChars() - memoryChars - summaryChars,
                MIN_HISTORY_CHARS);
        return new Context(memories, summary, historyMaxChars);
    }

    private static String truncate(String value, int max) {
        if (value == null || value.isBlank() || max <= 0) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max) + "...";
    }
}
