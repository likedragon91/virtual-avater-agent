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

    @Data
    public static class Context {
        private int maxMessages = 20;
        private int maxChars = 12000;
    }

    @Data
    public static class Tool {
        private int maxRounds = 3;
        private int maxCallsPerRound = 5;
        private int timeoutMs = 5000;
    }
}
