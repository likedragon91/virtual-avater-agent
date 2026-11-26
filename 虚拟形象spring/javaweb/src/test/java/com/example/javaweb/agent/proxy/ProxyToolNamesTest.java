package com.example.javaweb.agent.proxy;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProxyToolNamesTest {

    @Test
    void recognizesProxyInjectedTools() {
        List<String> reserved = List.of(
                "compress", "decompress", "search_context",
                "acp_status", "absorb", "acp_rule");
        for (String name : reserved) {
            assertThat(ProxyToolNames.isReserved(name)).as(name).isTrue();
        }
    }

    @Test
    void leavesBusinessToolsAlone() {
        assertThat(ProxyToolNames.isReserved("query_tasks")).isFalse();
        assertThat(ProxyToolNames.isReserved("search_memories")).isFalse();
        assertThat(ProxyToolNames.isReserved("search_web")).isFalse();
        assertThat(ProxyToolNames.isReserved("compress_context")).isFalse();
        assertThat(ProxyToolNames.isReserved(null)).isFalse();
    }
}
