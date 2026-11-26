package com.example.javaweb.service;

import com.example.javaweb.dto.agent.LlmStreamResult;
import com.example.javaweb.dto.agent.LlmToolCall;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentChatServiceProxyFilterTest {

    @Test
    void keepsBusinessToolsAndDropsProxyTools() {
        LlmStreamResult result = new LlmStreamResult();
        result.setToolCalls(List.of(
                call("compress"),
                call("query_tasks"),
                call("search_context"),
                call("save_memory")));

        assertThat(AgentChatService.filterProxyToolCalls(result))
                .extracting(LlmToolCall::getName)
                .containsExactly("query_tasks", "save_memory");
    }

    @Test
    void returnsEmptyWhenOnlyProxyTools() {
        LlmStreamResult result = new LlmStreamResult();
        result.setToolCalls(List.of(call("compress"), call("acp_status")));

        assertThat(AgentChatService.filterProxyToolCalls(result)).isEmpty();
    }

    @Test
    void returnsEmptyWhenNoToolCalls() {
        assertThat(AgentChatService.filterProxyToolCalls(new LlmStreamResult())).isEmpty();
        assertThat(AgentChatService.filterProxyToolCalls(null)).isEmpty();
    }

    private static LlmToolCall call(String name) {
        LlmToolCall call = new LlmToolCall();
        call.setId("call_" + name);
        call.setName(name);
        call.setArguments("{}");
        return call;
    }
}
