package com.example.javaweb.service;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.javaweb.agent.memory.ContextAssembler;
import com.example.javaweb.agent.performance.AssistantPerformanceStream;
import com.example.javaweb.agent.proxy.ProxyToolNames;
import com.example.javaweb.agent.style.ResponseStylePlanner;
import com.example.javaweb.agent.style.ResponseStylePlanner.PromptPlan;
import com.example.javaweb.agent.style.SystemPromptComposer;
import com.example.javaweb.dto.agent.LlmStreamResult;
import com.example.javaweb.dto.agent.LlmToolCall;
import com.example.javaweb.dto.projection.ProfileRow;
import com.example.javaweb.dto.agent.ToolContext;
import com.example.javaweb.dto.agent.ToolExecutionResult;
import com.example.javaweb.dto.request.SendMessageCommandDTO;
import com.example.javaweb.config.AgentProperties;
import com.example.javaweb.config.DeepSeekProperties;
import com.example.javaweb.entity.ChatMessage;
import com.example.javaweb.entity.ChatSession;
import com.example.javaweb.entity.LlmCallLog;
import com.example.javaweb.entity.ToolCallLog;
import com.example.javaweb.enums.ResponseMode;
import com.example.javaweb.exception.BusinessException;
import com.example.javaweb.exception.LlmException;
import com.example.javaweb.mapper.AgentDataMapper;
import com.example.javaweb.mapper.ChatMessageMapper;
import com.example.javaweb.mapper.LlmCallLogMapper;
import com.example.javaweb.mapper.ToolCallLogMapper;
import com.example.javaweb.persona.PersonaCatalog;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Agent 会话运行时：流式回答、工具循环、中断、落库。
 * 所有数据库访问都在模型调用之外，模型调用期间不持有事务。
 */
@Service
public class AgentChatService {
    private static final Logger log = LoggerFactory.getLogger(AgentChatService.class);
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Resource
    private AgentSessionService sessionService;

    @Resource
    private ChatMessageMapper messageMapper;

    @Resource
    private ToolCallLogMapper toolCallLogMapper;

    @Resource
    private LlmCallLogMapper llmCallLogMapper;

    @Resource
    private AgentDataMapper agentDataMapper;

    @Resource
    private PersonaService personaService;

    @Resource
    private DeepSeekClient deepSeekClient;

    @Resource
    private AgentToolService toolService;

    @Resource
    private AgentProperties agentProperties;

    @Resource
    private DeepSeekProperties deepSeekProperties;

    @Resource
    private WebSearchClient webSearchClient;

    @Resource
    private ResponseStylePlanner responseStylePlanner;

    @Resource
    private SystemPromptComposer systemPromptComposer;

    @Resource
    private ContextAssembler contextAssembler;

    @Resource
    private ConversationSummaryService conversationSummaryService;

    @Resource(name = "agentExecutor")
    private Executor agentExecutor;

    /** runId -> 是否请求取消 */
    private final Map<Long, AtomicBoolean> cancelFlags = new ConcurrentHashMap<>();

    public SseEmitter stream(Long sessionId, Long userId, SendMessageCommandDTO command) {
        ChatSession session = sessionService.requireOwned(userId, sessionId);
        if (session.getStatus() != null && session.getStatus() == 0) {
            throw new BusinessException("SESSION_ARCHIVED", "该会话已归档");
        }

        Long duplicate = messageMapper.selectCount(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, sessionId)
                .eq(ChatMessage::getClientMessageId, command.getClientMessageId()));
        if (duplicate != null && duplicate > 0) {
            throw new BusinessException("DUPLICATE_MESSAGE", "请勿重复发送同一条消息");
        }

        ChatMessage userMessage = new ChatMessage();
        userMessage.setSessionId(sessionId);
        userMessage.setUserId(userId);
        userMessage.setSenderRole("user");
        userMessage.setContent(command.getContent().trim());
        userMessage.setContentType("text");
        userMessage.setStatus(1);
        userMessage.setClientMessageId(command.getClientMessageId());
        try {
            messageMapper.insert(userMessage);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("DUPLICATE_MESSAGE", "请勿重复发送同一条消息");
        }

        ChatMessage assistant = new ChatMessage();
        assistant.setSessionId(sessionId);
        assistant.setUserId(userId);
        assistant.setSenderRole("assistant");
        assistant.setContent("");
        assistant.setContentType("text");
        assistant.setStatus(0);
        assistant.setModel(deepSeekProperties.getModel());
        messageMapper.insert(assistant);

        sessionService.touch(sessionId, userId, 2);

        Long runId = assistant.getId();
        SseEmitter emitter = new SseEmitter(0L);
        cancelFlags.put(runId, new AtomicBoolean(false));
        emitter.onCompletion(() -> cancelFlags.remove(runId));
        emitter.onTimeout(() -> cancelFlags.remove(runId));
        emitter.onError(error -> cancelFlags.remove(runId));

        try {
            agentExecutor.execute(() -> run(session, userMessage, assistant, emitter,
                    command.getResponseMode()));
        } catch (Exception e) {
            cancelFlags.remove(runId);
            markMessage(assistant, "", 2);
            log.error("submit agent run failed, runId={}", runId, e);
            throw new BusinessException("AGENT_BUSY", "当前对话繁忙，请稍后重试");
        }
        return emitter;
    }

    public void cancel(Long userId, Long runId) {
        ChatMessage message = messageMapper.selectById(runId);
        if (message == null || !"assistant".equals(message.getSenderRole())) {
            throw new BusinessException("NOT_FOUND", "运行不存在");
        }
        if (!message.getUserId().equals(userId)) {
            throw new BusinessException("FORBIDDEN", "没有权限操作该运行");
        }
        AtomicBoolean flag = cancelFlags.get(runId);
        if (flag != null) {
            flag.set(true);
        }
    }

    private void run(ChatSession session, ChatMessage userMessage,
                     ChatMessage assistant, SseEmitter emitter, String responseModeOverride) {
        Long runId = assistant.getId();
        AtomicBoolean cancelled = cancelFlags.get(runId);
        StringBuilder answer = new StringBuilder();
        AssistantPerformanceStream performance = createPerformanceStream(emitter);
        int totalCompletionTokens = 0;
        String lastFinishReason = null;

        try {
            try {
                send(emitter, "meta", Map.of(
                        "runId", runId,
                        "sessionId", session.getId(),
                        "assistantMessageId", runId));
            } catch (IOException e) {
                throw new ClientGoneException();
            }

            ContextAssembler.Context context = contextAssembler.assemble(
                    session.getUserId(), session, userMessage.getContent());
            SystemPrompt systemPrompt = buildSystemPrompt(
                    session, userMessage.getContent(), responseModeOverride, context);
            PromptPlan plan = systemPrompt.plan();
            Integer maxTokens = resolveMaxTokens(plan);
            log.info("agent run started, runId={}, mode={}, crisis={}, maxTokens={}, memories={}, summary={}",
                    runId, plan.mode(), plan.crisis(), maxTokens,
                    context.memories().size(), context.summary() != null);

            List<Map<String, Object>> messages = buildMessages(
                    session, userMessage, systemPrompt.text(), context.historyMaxChars());
            List<Map<String, Object>> tools = toolService.definitions();
            ToolContext toolContext = ToolContext.builder()
                    .userId(session.getUserId())
                    .sessionId(session.getId())
                    .assistantMessageId(runId)
                    .userMessageId(userMessage.getId())
                    .build();

            int rounds = 0;
            while (true) {
                checkCancelled(cancelled);
                long llmStart = System.currentTimeMillis();
                LlmStreamResult result;
                try {
                    result = deepSeekClient.streamChat(messages, tools, maxTokens,
                            String.valueOf(session.getId()), delta -> {
                        checkCancelled(cancelled);
                        if (performance != null) {
                            try {
                                performance.accept(delta);
                            } catch (IOException e) {
                                throw new UncheckedIOException(e);
                            }
                        } else {
                            answer.append(delta);
                            try {
                                send(emitter, "delta", Map.of("text", delta));
                            } catch (IOException e) {
                                throw new UncheckedIOException(e);
                            }
                        }
                    });
                } catch (UncheckedIOException e) {
                    throw new ClientGoneException();
                }

                if (result.getCompletionTokens() != null) {
                    totalCompletionTokens += result.getCompletionTokens();
                }
                lastFinishReason = result.getFinishReason();
                saveLlmLog(session.getUserId(), runId, result,
                        System.currentTimeMillis() - llmStart, "OK");

                List<LlmToolCall> toolCalls = filterProxyToolCalls(result);
                if (toolCalls.isEmpty()) {
                    break;
                }
                // 被 max_tokens 截断时，工具参数可能是半截 JSON，执行会污染后续上下文
                if ("length".equals(lastFinishReason)) {
                    log.warn("agent output truncated by max_tokens, runId={}, mode={}, cap={}",
                            runId, plan.mode(), maxTokens);
                    break;
                }
                rounds++;
                if (rounds > agentProperties.getTool().getMaxRounds()) {
                    log.warn("agent tool rounds exceeded, runId={}", runId);
                    break;
                }

                // 工具调用前后不粘成一句：先把当前句读完
                if (performance != null) {
                    try {
                        performance.flushSegment();
                    } catch (IOException e) {
                        throw new ClientGoneException();
                    }
                }

                int index = 0;
                for (LlmToolCall call : toolCalls) {
                    index++;
                    if (call.getId() == null || call.getId().isBlank()) {
                        call.setId("call_" + index);
                    }
                }
                messages.add(toolCallAssistantMessage(result, toolCalls));

                int callIndex = 0;
                for (LlmToolCall call : toolCalls) {
                    callIndex++;
                    if (callIndex > agentProperties.getTool().getMaxCallsPerRound()) {
                        break;
                    }
                    checkCancelled(cancelled);
                    send(emitter, "tool_start", Map.of(
                            "callId", call.getId(),
                            "tool", call.getName()));

                    long toolStart = System.currentTimeMillis();
                    ToolExecutionResult execution = toolService.execute(
                            call.getName(), call.getArguments(), toolContext);
                    long duration = System.currentTimeMillis() - toolStart;
                    saveToolLog(runId, session, call, execution, duration);

                    send(emitter, "tool_end", Map.of(
                            "callId", call.getId(),
                            "ok", execution.isOk(),
                            "summary", execution.getSummary() == null ? "" : execution.getSummary()));
                    messages.add(toolResultMessage(call.getId(), execution.getContent()));
                }
            }

            checkCancelled(cancelled);
            if (performance != null) {
                try {
                    performance.finish();
                } catch (IOException ignored) {
                    // 客户端已断开；剩余正文仍要落库
                }
            }

            String finalContent = performance != null ? performance.cleanedAnswer() : answer.toString();
            String emotionJson = performance != null ? performance.lastEmotionJson() : null;
            int speechCount = performance != null ? performance.speechCount() : 0;

            assistant.setContent(finalContent);
            assistant.setStatus(1);
            assistant.setEmotion(emotionJson);
            assistant.setTokens(totalCompletionTokens > 0 ? Integer.valueOf(totalCompletionTokens) : null);
            messageMapper.updateById(assistant);

            try {
                conversationSummaryService.summarizeAsync(session.getId());
            } catch (Exception e) {
                log.warn("submit conversation summary failed, sessionId={}", session.getId(), e);
            }

            Map<String, Object> donePayload = new LinkedHashMap<>();
            donePayload.put("runId", runId);
            donePayload.put("messageId", runId);
            donePayload.put("finishReason", lastFinishReason == null ? "stop" : lastFinishReason);
            donePayload.put("usage", Map.of("completionTokens", totalCompletionTokens));
            donePayload.put("speechCount", speechCount);
            if (emotionJson != null) {
                donePayload.put("emotion", emotionJson);
            }

            try {
                send(emitter, "done", donePayload);
            } catch (IOException ignored) {
                // 客户端已断开，回答已经落库
            }
            completeQuietly(emitter);
        } catch (ClientGoneException e) {
            markMessage(assistant, contentForPersistence(answer, performance), 3);
            completeQuietly(emitter);
        } catch (RunCancelledException e) {
            markMessage(assistant, contentForPersistence(answer, performance), 3);
            try {
                send(emitter, "done", Map.of(
                        "runId", runId,
                        "messageId", runId,
                        "finishReason", "cancelled"));
            } catch (Exception ignored) {
                // 客户端可能已经断开
            }
            completeQuietly(emitter);
        } catch (LlmException e) {
            log.warn("llm failed, runId={}, code={}", runId, e.getCode());
            markMessage(assistant, contentForPersistence(answer, performance), 2);
            try {
                send(emitter, "error", Map.of("code", e.getCode(), "msg", e.getMessage()));
            } catch (Exception ignored) {
                // 客户端可能已经断开
            }
            completeQuietly(emitter);
        } catch (Exception e) {
            log.error("agent run failed, runId={}", runId, e);
            markMessage(assistant, contentForPersistence(answer, performance), 2);
            try {
                send(emitter, "error", Map.of("code", "AGENT_ERROR", "msg", "回答生成失败，请重试"));
            } catch (Exception ignored) {
                // 客户端可能已经断开
            }
            completeQuietly(emitter);
        } finally {
            cancelFlags.remove(runId);
        }
    }

    private List<Map<String, Object>> buildMessages(ChatSession session, ChatMessage userMessage,
                                                    String systemPrompt, int historyMaxChars) {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));

        for (ChatMessage history : loadHistory(
                session.getId(), session.getUserId(), userMessage.getId(), historyMaxChars)) {
            if (history.getContent() == null || history.getContent().isBlank()) {
                continue;
            }
            messages.add(Map.of("role", history.getSenderRole(), "content", history.getContent()));
        }
        messages.add(Map.of("role", "user", "content", userMessage.getContent()));
        return messages;
    }

    private List<ChatMessage> loadHistory(Long sessionId, Long userId, Long beforeId,
                                          int historyMaxChars) {
        int maxMessages = Math.max(agentProperties.getContext().getMaxMessages(), 2);
        LambdaQueryWrapper<ChatMessage> query = new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, sessionId)
                .eq(ChatMessage::getUserId, userId)
                .eq(ChatMessage::getStatus, 1)
                .in(ChatMessage::getSenderRole, List.of("user", "assistant"))
                .lt(beforeId != null, ChatMessage::getId, beforeId)
                .orderByDesc(ChatMessage::getId)
                .last("LIMIT " + maxMessages);
        List<ChatMessage> rows = messageMapper.selectList(query);
        Collections.reverse(rows);

        int maxChars = Math.max(historyMaxChars, 1000);
        LinkedList<ChatMessage> selected = new LinkedList<>();
        int chars = 0;
        for (int i = rows.size() - 1; i >= 0; i--) {
            ChatMessage message = rows.get(i);
            int length = message.getContent() == null ? 0 : message.getContent().length();
            if (!selected.isEmpty() && chars + length > maxChars) {
                break;
            }
            selected.addFirst(message);
            chars += length;
        }
        return selected;
    }

    private SystemPrompt buildSystemPrompt(ChatSession session, String latestUserContent,
                                           String responseModeOverride,
                                           ContextAssembler.Context context) {
        ProfileRow profile = null;
        try {
            profile = agentDataMapper.profile(session.getUserId());
        } catch (Exception e) {
            log.warn("load profile failed, userId={}", session.getUserId(), e);
        }

        String nickname = profile != null && profile.getNickname() != null
                ? profile.getNickname() : "你";
        String stage = profile != null && profile.getStageCode() != null
                ? profile.getStageCode() : "S0";
        Object intimacy = profile != null && profile.getIntimacyTotal() != null
                ? profile.getIntimacyTotal() : 0;
        String capStage = profile != null && profile.getUserCapStage() != null
                ? profile.getUserCapStage() : "标准";

        PersonaService.StylePrompt style = null;
        try {
            style = personaService.buildStylePrompt(session.getUserId(), session, profile);
        } catch (Exception e) {
            // 人格配置异常不能阻塞聊天，回退到固定人设
            log.warn("load persona prompt failed, userId={}", session.getUserId(), e);
        }
        PersonaCatalog.Traits traits = style != null ? style.traits()
                : PersonaCatalog.defaults(PersonaCatalog.DEFAULT_CODE);

        PromptPlan plan = responseStylePlanner.plan(
                latestUserContent, ResponseMode.from(responseModeOverride));

        String prompt = systemPromptComposer.compose(new SystemPromptComposer.PromptInput(
                LocalDateTime.now().format(DATE_TIME_FORMATTER),
                nickname,
                stage,
                intimacy,
                capStage,
                style != null ? style.text() : "",
                traits,
                plan,
                webSearchClient.isConfigured(),
                agentProperties.getPerformance() != null && agentProperties.getPerformance().isEnabled(),
                context.memories(),
                context.summary()));
        return new SystemPrompt(prompt, plan);
    }

    /**
     * 仅对短回复模式收紧 max_tokens；危机、知识、任务类保持全局上限。
     * 默认关闭，开启前先观察 finish_reason=length 的比例。
     */
    private Integer resolveMaxTokens(PromptPlan plan) {
        AgentProperties.ResponseStyle config = agentProperties.getResponseStyle();
        if (config == null || !config.isTokenCapEnabled()
                || plan == null || plan.crisis() || !plan.mode().isShort()) {
            return null;
        }
        return Math.max(config.getCasualMaxTokens(), config.getMinMaxTokens());
    }

    private record SystemPrompt(String text, PromptPlan plan) {
    }

    private AssistantPerformanceStream createPerformanceStream(SseEmitter emitter) {
        AgentProperties.Performance config = agentProperties.getPerformance();
        if (config == null || !config.isEnabled()) {
            return null;
        }
        return new AssistantPerformanceStream(new AssistantPerformanceStream.EventSink() {
            @Override
            public void delta(String text) throws IOException {
                send(emitter, "delta", Map.of("text", text));
            }

            @Override
            public void act(Map<String, Object> payload) throws IOException {
                send(emitter, "act", payload);
            }

            @Override
            public void speech(Map<String, Object> payload) throws IOException {
                send(emitter, "speech", payload);
            }
        }, config.getMaxSpeechChars(), config.getMinSpeechChars());
    }

    private static String contentForPersistence(StringBuilder answer,
                                                AssistantPerformanceStream performance) {
        return performance != null ? performance.cleanedAnswer() : answer.toString();
    }

    /**
     * 代理注入的工具由代理自己消费；如果泄漏到客户端，过滤掉，避免进入本地工具循环。
     */
    static List<LlmToolCall> filterProxyToolCalls(LlmStreamResult result) {
        if (result == null || !result.hasToolCalls()) {
            return List.of();
        }
        List<LlmToolCall> filtered = new ArrayList<>(result.getToolCalls().size());
        for (LlmToolCall call : result.getToolCalls()) {
            if (ProxyToolNames.isReserved(call.getName())) {
                log.debug("ignore proxy-side tool call: {}", call.getName());
                continue;
            }
            filtered.add(call);
        }
        return filtered;
    }

    private Map<String, Object> toolCallAssistantMessage(LlmStreamResult result,
                                                         List<LlmToolCall> toolCalls) {
        Map<String, Object> assistant = new LinkedHashMap<>();
        assistant.put("role", "assistant");
        assistant.put("content", result.getContent() == null || result.getContent().isBlank()
                ? null : result.getContent());

        List<Map<String, Object>> calls = new ArrayList<>();
        for (LlmToolCall call : toolCalls) {
            Map<String, Object> function = new LinkedHashMap<>();
            function.put("name", call.getName());
            function.put("arguments", call.getArguments() == null || call.getArguments().isBlank()
                    ? "{}" : call.getArguments());

            Map<String, Object> toolCall = new LinkedHashMap<>();
            toolCall.put("id", call.getId());
            toolCall.put("type", "function");
            toolCall.put("function", function);
            calls.add(toolCall);
        }
        assistant.put("tool_calls", calls);
        return assistant;
    }

    private Map<String, Object> toolResultMessage(String callId, String content) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "tool");
        message.put("tool_call_id", callId);
        message.put("content", content == null || content.isBlank() ? "{}" : content);
        return message;
    }

    private void send(SseEmitter emitter, String event, Object data) throws IOException {
        emitter.send(SseEmitter.event()
                .name(event)
                .data(JSONUtil.toJsonStr(data), MediaType.APPLICATION_JSON));
    }

    private void saveToolLog(Long runId, ChatSession session, LlmToolCall call,
                             ToolExecutionResult execution, long durationMs) {
        try {
            ToolCallLog logRow = new ToolCallLog();
            logRow.setMessageId(runId);
            logRow.setSessionId(session.getId());
            logRow.setUserId(session.getUserId());
            logRow.setCallId(call.getId());
            logRow.setToolName(call.getName());
            logRow.setArguments(sanitizeArguments(call.getArguments()));
            logRow.setResultSummary(truncate(execution.getSummary(), 500));
            logRow.setStatus(execution.getStatus());
            logRow.setDurationMs((int) Math.min(Math.max(durationMs, 0), Integer.MAX_VALUE));
            toolCallLogMapper.insert(logRow);
        } catch (Exception e) {
            log.warn("save tool log failed, runId={}", runId, e);
        }
    }

    private void saveLlmLog(Long userId, Long runId, LlmStreamResult result,
                            long latencyMs, String status) {
        try {
            LlmCallLog logRow = new LlmCallLog();
            logRow.setUserId(userId);
            logRow.setScene("agent_chat");
            logRow.setModel(deepSeekProperties.getModel());
            logRow.setPromptTokens(result.getPromptTokens());
            logRow.setCompletionTokens(result.getCompletionTokens());
            logRow.setLatencyMs((int) Math.min(Math.max(latencyMs, 0), Integer.MAX_VALUE));
            logRow.setStatus(status);
            logRow.setTraceId(String.valueOf(runId));
            llmCallLogMapper.insert(logRow);
        } catch (Exception e) {
            log.warn("save llm log failed, runId={}", runId, e);
        }
    }

    private void markMessage(ChatMessage assistant, String content, int status) {
        try {
            assistant.setContent(content == null ? "" : content);
            assistant.setStatus(status);
            messageMapper.updateById(assistant);
        } catch (Exception e) {
            log.warn("mark message failed, messageId={}", assistant.getId(), e);
        }
    }

    private static void checkCancelled(AtomicBoolean cancelled) {
        if (cancelled != null && cancelled.get()) {
            throw new RunCancelledException();
        }
    }

    private static void completeQuietly(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (Exception ignored) {
            // 客户端可能已经断开
        }
    }

    private static String sanitizeArguments(String raw) {
        if (raw == null || raw.isBlank()) {
            return "{}";
        }
        try {
            JSONObject source = JSONUtil.parseObj(raw);
            JSONObject clean = new JSONObject();
            for (String key : source.keySet()) {
                String lower = key.toLowerCase();
                if (lower.contains("password") || lower.contains("token")
                        || lower.contains("secret") || lower.contains("api_key")) {
                    continue;
                }
                // 记忆正文不完整落审计日志，只保留占位
                if (lower.equals("content") || lower.equals("note")) {
                    clean.set(key, "[redacted]");
                    continue;
                }
                Object value = source.get(key);
                if (value instanceof String text && text.length() > 200) {
                    value = text.substring(0, 200) + "...";
                }
                clean.set(key, value);
            }
            return clean.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max) + "...";
    }

    private static class RunCancelledException extends RuntimeException {
    }

    private static class ClientGoneException extends RuntimeException {
    }
}
