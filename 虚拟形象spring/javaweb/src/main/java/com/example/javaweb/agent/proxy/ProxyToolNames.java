package com.example.javaweb.agent.proxy;

import java.util.Set;

/**
 * 上下文代理注入的工具名。正常由代理在服务端消费；
 * 如果出现在客户端流里，说明代理行为异常，需要过滤掉，避免进入本地工具循环。
 */
public final class ProxyToolNames {

    private static final Set<String> RESERVED = Set.of(
            "compress", "decompress", "search_context",
            "acp_status", "absorb", "acp_rule");

    private ProxyToolNames() {
    }

    public static boolean isReserved(String toolName) {
        return toolName != null && RESERVED.contains(toolName);
    }
}
