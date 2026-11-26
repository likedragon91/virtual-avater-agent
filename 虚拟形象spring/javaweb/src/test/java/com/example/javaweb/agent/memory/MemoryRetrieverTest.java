package com.example.javaweb.agent.memory;

import com.example.javaweb.config.AgentProperties;
import com.example.javaweb.dto.projection.MemoryCandidate;
import com.example.javaweb.dto.projection.MemoryRow;
import com.example.javaweb.mapper.MemoryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemoryRetrieverTest {

    @Mock
    private MemoryMapper memoryMapper;

    @Mock
    private EmbeddingService embeddingService;

    @InjectMocks
    private MemoryRetriever retriever;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(retriever, "agentProperties", new AgentProperties());
    }

    @Test
    void returnsEmptyWhenNothingMatches() {
        assertThat(retriever.retrieve(1L, "你好")).isEmpty();
    }

    @Test
    void nullUserReturnsEmpty() {
        assertThat(retriever.retrieve(null, "你好")).isEmpty();
    }

    @Test
    void profileAndPreferenceAreAlwaysIncludedFirst() {
        MemoryCandidate episode = candidate(1L, "EPISODE", "用户今天面试很顺利", 5, 4.0);
        MemoryCandidate profile = candidate(2L, "PROFILE", "用户叫小明", 5, 0);
        MemoryCandidate preference = candidate(3L, "PREFERENCE", "喜欢冰美式", 4, 0);
        when(memoryMapper.searchScored(1L, "面试怎么样", 20)).thenReturn(List.of(episode));
        when(memoryMapper.selectFloorByType(1L, "PROFILE", 1)).thenReturn(List.of(profile));
        when(memoryMapper.selectFloorByType(1L, "PREFERENCE", 1)).thenReturn(List.of(preference));

        List<MemoryRow> rows = retriever.retrieve(1L, "面试怎么样");

        assertThat(rows).extracting(MemoryRow::getMemoryType)
                .containsExactly("PROFILE", "PREFERENCE", "EPISODE");
    }

    @Test
    void longContentIsTruncatedPerItemBudget() {
        String longContent = "a".repeat(300);
        when(memoryMapper.searchScored(1L, "回忆", 20))
                .thenReturn(List.of(candidate(1L, "EPISODE", longContent, 3, 1.0)));

        List<MemoryRow> rows = retriever.retrieve(1L, "回忆");

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getContent()).hasSize(123).endsWith("...");
    }

    @Test
    void identicalContentIsDeduplicated() {
        when(memoryMapper.searchScored(1L, "咖啡", 20)).thenReturn(List.of(
                candidate(1L, "PREFERENCE", "喜欢冰美式", 4, 2.0),
                candidate(2L, "EPISODE", "喜欢 冰美式", 4, 1.0)));

        List<MemoryRow> rows = retriever.retrieve(1L, "咖啡");

        assertThat(rows).hasSize(1);
    }

    @Test
    void topKLimitIsRespected() {
        List<MemoryCandidate> many = java.util.stream.IntStream.range(0, 10)
                .mapToObj(i -> candidate((long) i, "EPISODE", "记忆" + i, 3, 5.0 - i))
                .toList();
        when(memoryMapper.searchScored(1L, "今天", 20)).thenReturn(many);

        assertThat(retriever.retrieve(1L, "今天")).hasSize(6);
    }

    @Test
    void blankQueryUsesRecentPool() {
        when(memoryMapper.searchRecent(1L, 20))
                .thenReturn(List.of(candidate(1L, "EPISODE", "最近的事", 3, 0)));

        List<MemoryRow> rows = retriever.retrieve(1L, "  ");

        assertThat(rows).hasSize(1);
        verify(memoryMapper, never()).searchScored(anyLong(), anyString(), anyInt());
    }

    private static MemoryCandidate candidate(Long id, String type, String content,
                                             int importance, double score) {
        MemoryCandidate candidate = new MemoryCandidate();
        candidate.setId(id);
        candidate.setMemoryType(type);
        candidate.setContent(content);
        candidate.setImportance(importance);
        candidate.setScore(score);
        candidate.setOccurredAt(LocalDateTime.now().minusDays(1));
        return candidate;
    }
}
