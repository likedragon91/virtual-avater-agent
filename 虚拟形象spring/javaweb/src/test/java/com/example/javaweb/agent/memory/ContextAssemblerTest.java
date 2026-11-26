package com.example.javaweb.agent.memory;

import com.example.javaweb.config.AgentProperties;
import com.example.javaweb.dto.projection.MemoryRow;
import com.example.javaweb.entity.ChatSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContextAssemblerTest {

    @Mock
    private MemoryRetriever memoryRetriever;

    @InjectMocks
    private ContextAssembler assembler;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(assembler, "agentProperties", new AgentProperties());
    }

    @Test
    void budgetSubtractsMemoryAndSummary() {
        String summary = "s".repeat(900);
        when(memoryRetriever.retrieve(anyLong(), any())).thenReturn(List.of(memory("m".repeat(100))));

        ContextAssembler.Context context = assembler.assemble(1L, session(summary), "你好");

        assertThat(context.summary()).hasSize(803).endsWith("...");
        assertThat(context.historyMaxChars()).isEqualTo(12000 - 100 - 803);
        assertThat(context.memories()).hasSize(1);
    }

    @Test
    void noSummaryLeavesItNull() {
        when(memoryRetriever.retrieve(anyLong(), any())).thenReturn(List.of());

        ContextAssembler.Context context = assembler.assemble(1L, session(null), "你好");

        assertThat(context.summary()).isNull();
        assertThat(context.historyMaxChars()).isEqualTo(12000);
    }

    @Test
    void historyBudgetNeverDropsBelowFloor() {
        AgentProperties properties = new AgentProperties();
        properties.getContext().setMaxChars(1500);
        ReflectionTestUtils.setField(assembler, "agentProperties", properties);
        when(memoryRetriever.retrieve(anyLong(), any())).thenReturn(List.of(memory("m".repeat(500))));

        ContextAssembler.Context context = assembler.assemble(1L, session(null), "你好");

        assertThat(context.historyMaxChars()).isEqualTo(1000);
    }

    private static ChatSession session(String summary) {
        ChatSession session = new ChatSession();
        session.setId(1L);
        session.setSummary(summary);
        return session;
    }

    private static MemoryRow memory(String content) {
        MemoryRow row = new MemoryRow();
        row.setId(1L);
        row.setMemoryType("EPISODE");
        row.setContent(content);
        return row;
    }
}
