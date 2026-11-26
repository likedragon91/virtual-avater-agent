package com.example.javaweb.service;

import com.example.javaweb.config.AgentProperties;
import com.example.javaweb.entity.ChatMessage;
import com.example.javaweb.entity.ChatSession;
import com.example.javaweb.mapper.ChatMessageMapper;
import com.example.javaweb.mapper.ChatSessionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.concurrent.Executor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationSummaryServiceTest {

    @Mock
    private ChatMessageMapper messageMapper;

    @Mock
    private ChatSessionMapper sessionMapper;

    @Mock
    private DeepSeekClient deepSeekClient;

    @Mock
    private Executor summaryExecutor;

    @InjectMocks
    private ConversationSummaryService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "agentProperties", new AgentProperties());
    }

    @Test
    void writesSummaryWhenThresholdReached() {
        when(sessionMapper.selectById(1L)).thenReturn(session());
        when(messageMapper.selectList(any())).thenReturn(messages(12));
        when(deepSeekClient.complete(any(), anyInt(), any())).thenReturn(" 用户最近在准备面试。 ");

        service.summarize(1L);

        verify(sessionMapper).updateSummary(eq(1L), eq("用户最近在准备面试。"), eq(12L), any());
        // 摘要请求使用独立会话键，避免污染聊天会话的压缩状态
        verify(deepSeekClient).complete(any(), anyInt(), eq("summary-1"));
    }

    @Test
    void doesNothingBelowThreshold() {
        when(sessionMapper.selectById(1L)).thenReturn(session());
        when(messageMapper.selectList(any())).thenReturn(messages(3));

        service.summarize(1L);

        verify(sessionMapper, never()).updateSummary(any(), any(), any(), any());
        verify(deepSeekClient, never()).complete(any(), anyInt(), any());
    }

    @Test
    void blankModelOutputDoesNotOverwriteSummary() {
        when(sessionMapper.selectById(1L)).thenReturn(session());
        when(messageMapper.selectList(any())).thenReturn(messages(12));
        when(deepSeekClient.complete(any(), anyInt(), any())).thenReturn("   ");

        service.summarize(1L);

        verify(sessionMapper, never()).updateSummary(any(), any(), any(), any());
    }

    @Test
    void missingSessionIsIgnored() {
        when(sessionMapper.selectById(1L)).thenReturn(null);

        service.summarize(1L);

        verify(messageMapper, never()).selectList(any());
    }

    private static ChatSession session() {
        ChatSession session = new ChatSession();
        session.setId(1L);
        session.setSummaryMessageId(0L);
        return session;
    }

    private static List<ChatMessage> messages(int count) {
        return java.util.stream.IntStream.rangeClosed(1, count)
                .mapToObj(index -> {
                    ChatMessage message = new ChatMessage();
                    message.setId((long) index);
                    message.setSessionId(1L);
                    message.setSenderRole(index % 2 == 0 ? "assistant" : "user");
                    message.setContent("消息" + index);
                    message.setStatus(1);
                    return message;
                })
                .toList();
    }
}
