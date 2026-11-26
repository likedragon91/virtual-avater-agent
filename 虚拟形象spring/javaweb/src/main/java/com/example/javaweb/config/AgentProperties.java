package com.example.javaweb.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Agent 运行参数：上下文预算、工具循环上限、工具超时。
 */
@Data
@ConfigurationProperties(prefix = "agent")
@Component
public class AgentProperties {
    private Context context = new Context();
    private Tool tool = new Tool();
    private Performance performance = new Performance();
    private ResponseStyle responseStyle = new ResponseStyle();

    @Data
    public static class Context {
        private int maxMessages = 20;
        private int maxChars = 12000;
        /** 进入提示词的记忆条数 */
        private int memoryTopK = 6;
        /** 记忆段总字数预算 */
        private int memoryMaxChars = 1200;
        /** 单条记忆截断长度 */
        private int memoryItemMaxChars = 120;
        /** 参与重排的候选数量 */
        private int memoryCandidateLimit = 20;
        /** 会话摘要注入上限 */
        private int summaryMaxChars = 800;
        /** 未摘要消息达到该数量时触发摘要 */
        private int summaryTriggerMessages = 12;
        /** 摘要模型输出上限 */
        private int summaryMaxTokens = 400;
    }

    @Data
    public static class Tool {
        private int maxRounds = 3;
        private int maxCallsPerRound = 5;
        private int timeoutMs = 5000;
    }

    @Data
    public static class Performance {
        /** 是否启用 <act/> 表演标签与 act/speech SSE 事件 */
        private boolean enabled = true;
        /** 单句朗读文本上限，避免 TTS 请求过长 */
        private int maxSpeechChars = 120;
        private int minSpeechChars = 12;
    }

    /**
     * 回复风格。提示词约束始终生效；token 上限只是安全网，默认关闭。
     */
    @Data
    public static class ResponseStyle {
        /** 开启后对闲聊/情感模式设置宽松的 max_tokens 兜底 */
        private boolean tokenCapEnabled = false;
        /** 短回复模式的 max_tokens；需远高于目标句数，避免截断工具调用 */
        private int casualMaxTokens = 1024;
        /** 上限下限，防止配置过小导致半句截断 */
        private int minMaxTokens = 1024;
    }
}
