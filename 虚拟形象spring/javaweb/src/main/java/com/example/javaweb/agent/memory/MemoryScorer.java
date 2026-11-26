package com.example.javaweb.agent.memory;

import com.example.javaweb.dto.projection.MemoryCandidate;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 记忆重排打分：全文相关度 + 向量相似度 + 重要度 + 时间衰减。
 * 没有向量时向量项为 0，不影响其余项的相对排序。
 */
public final class MemoryScorer {

    public static final double TEXT_WEIGHT = 0.45;
    public static final double VECTOR_WEIGHT = 0.35;
    public static final double IMPORTANCE_WEIGHT = 0.10;
    public static final double RECENCY_WEIGHT = 0.10;

    /** 记忆的“半衰期”，180 天后权重衰减到约 0.37 */
    private static final double HALF_LIFE_DAYS = 180.0;

    private MemoryScorer() {
    }

    public static double score(MemoryCandidate candidate, float[] queryVector, LocalDateTime now) {
        LocalDateTime anchor = candidate.getOccurredAt() != null
                ? candidate.getOccurredAt() : candidate.getCreatedAt();
        return TEXT_WEIGHT * textScore(candidate.getScore())
                + VECTOR_WEIGHT * vectorScore(candidate, queryVector)
                + IMPORTANCE_WEIGHT * importanceScore(candidate.getImportance())
                + RECENCY_WEIGHT * recencyScore(anchor, now);
    }

    /** MySQL ngram 分数没有固定上限，按经验归一到 0~1 */
    static double textScore(Double raw) {
        if (raw == null || raw <= 0) {
            return 0;
        }
        return Math.min(raw / 5.0, 1.0);
    }

    static double vectorScore(MemoryCandidate candidate, float[] queryVector) {
        if (queryVector == null || candidate.getEmbedding() == null) {
            return 0;
        }
        double cosine = EmbeddingCodec.cosine(queryVector, candidate.getEmbedding());
        return Math.max(0, Math.min(cosine, 1.0));
    }

    static double importanceScore(Integer importance) {
        if (importance == null) {
            return 0.6;
        }
        int clamped = Math.max(0, Math.min(importance, 5));
        return clamped / 5.0;
    }

    static double recencyScore(LocalDateTime time, LocalDateTime now) {
        if (time == null) {
            return 0.5;
        }
        double days = Duration.between(time, now).toMinutes() / (60.0 * 24.0);
        if (days < 0) {
            days = 0;
        }
        return Math.exp(-days / HALF_LIFE_DAYS);
    }
}
