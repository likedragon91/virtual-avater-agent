package com.example.javaweb.agent.memory;

import com.example.javaweb.dto.projection.MemoryCandidate;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

class MemoryScorerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 18, 12, 0);

    @Test
    void higherFulltextScoreRanksHigher() {
        MemoryCandidate low = candidate(1L, 1.0, 3, NOW.minusDays(10), null);
        MemoryCandidate high = candidate(2L, 4.0, 3, NOW.minusDays(10), null);

        assertThat(MemoryScorer.score(high, null, NOW))
                .isGreaterThan(MemoryScorer.score(low, null, NOW));
    }

    @Test
    void vectorSimilarityMovesTheScore() {
        MemoryCandidate relevant = candidate(1L, 0, 3, NOW.minusDays(10),
                EmbeddingCodec.toBytes(new float[]{1f, 0f}));
        MemoryCandidate unrelated = candidate(2L, 0, 3, NOW.minusDays(10),
                EmbeddingCodec.toBytes(new float[]{0f, 1f}));
        float[] query = {1f, 0f};

        assertThat(MemoryScorer.score(relevant, query, NOW))
                .isGreaterThan(MemoryScorer.score(unrelated, query, NOW));
    }

    @Test
    void olderMemoriesDecayButNeverGoNegative() {
        double fresh = MemoryScorer.recencyScore(NOW.minusDays(1), NOW);
        double old = MemoryScorer.recencyScore(NOW.minusDays(500), NOW);

        assertThat(fresh).isGreaterThan(old);
        assertThat(old).isGreaterThan(0);
        assertThat(MemoryScorer.recencyScore(null, NOW)).isCloseTo(0.5, offset(1e-9));
    }

    @Test
    void importanceIsClampedToZeroOne() {
        assertThat(MemoryScorer.importanceScore(5)).isCloseTo(1.0, offset(1e-9));
        assertThat(MemoryScorer.importanceScore(99)).isCloseTo(1.0, offset(1e-9));
        assertThat(MemoryScorer.importanceScore(-1)).isCloseTo(0.0, offset(1e-9));
        assertThat(MemoryScorer.importanceScore(null)).isCloseTo(0.6, offset(1e-9));
    }

    @Test
    void textScoreIsClamped() {
        assertThat(MemoryScorer.textScore(0.0)).isZero();
        assertThat(MemoryScorer.textScore(null)).isZero();
        assertThat(MemoryScorer.textScore(50.0)).isCloseTo(1.0, offset(1e-9));
    }

    private static MemoryCandidate candidate(Long id, double score, int importance,
                                             LocalDateTime occurredAt, byte[] embedding) {
        MemoryCandidate candidate = new MemoryCandidate();
        candidate.setId(id);
        candidate.setMemoryType("EPISODE");
        candidate.setContent("content-" + id);
        candidate.setScore(score);
        candidate.setImportance(importance);
        candidate.setOccurredAt(occurredAt);
        candidate.setEmbedding(embedding);
        return candidate;
    }
}
