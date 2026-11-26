package com.example.javaweb.agent.memory;

import com.example.javaweb.config.AgentProperties;
import com.example.javaweb.dto.projection.MemoryCandidate;
import com.example.javaweb.dto.projection.MemoryRow;
import com.example.javaweb.mapper.MemoryMapper;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 按当前消息检索记忆：ngram 召回 → Java 侧重排 → 预算裁剪。
 * 单用户最多 200 条 ACTIVE 记忆，候选池足够小，无需外部向量库。
 */
@Component
public class MemoryRetriever {
    private static final Logger log = LoggerFactory.getLogger(MemoryRetriever.class);

    private static final double NEAR_DUPLICATE_COSINE = 0.95;

    @Resource
    private MemoryMapper memoryMapper;

    @Resource
    private AgentProperties agentProperties;

    @Resource
    private EmbeddingService embeddingService;

    public List<MemoryRow> retrieve(Long userId, String query) {
        if (userId == null) {
            return List.of();
        }
        AgentProperties.Context config = agentProperties.getContext();
        int candidateLimit = Math.max(config.getMemoryCandidateLimit(), 1);

        Map<Long, MemoryCandidate> candidates = new LinkedHashMap<>();
        if (query != null && !query.isBlank()) {
            try {
                addCandidates(candidates, memoryMapper.searchScored(userId, query.trim(), candidateLimit));
            } catch (Exception e) {
                log.warn("memory fulltext search failed, userId={}", userId, e);
            }
        }
        if (candidates.isEmpty()) {
            addCandidates(candidates, memoryMapper.searchRecent(userId, candidateLimit));
        }
        // 画像与偏好是长期事实，无论查询是否命中都保底进入候选
        addCandidates(candidates, memoryMapper.selectFloorByType(userId, "PROFILE", 1));
        addCandidates(candidates, memoryMapper.selectFloorByType(userId, "PREFERENCE", 1));
        if (candidates.isEmpty()) {
            return List.of();
        }

        float[] queryVector = embeddingService.embedOrNull(query);
        LocalDateTime now = LocalDateTime.now();
        Map<Long, Double> scores = new HashMap<>();
        for (MemoryCandidate candidate : candidates.values()) {
            scores.put(candidate.getId(), MemoryScorer.score(candidate, queryVector, now));
        }
        List<MemoryCandidate> ordered = new ArrayList<>(candidates.values());
        ordered.sort(Comparator.comparingDouble(
                (MemoryCandidate candidate) -> scores.getOrDefault(candidate.getId(), 0.0)).reversed());

        int topK = Math.max(config.getMemoryTopK(), 1);
        int itemMax = Math.max(config.getMemoryItemMaxChars(), 20);
        int maxChars = Math.max(config.getMemoryMaxChars(), itemMax);

        List<MemoryCandidate> picked = new ArrayList<>();
        List<MemoryRow> rows = new ArrayList<>();
        int chars = 0;

        for (String floorType : List.of("PROFILE", "PREFERENCE")) {
            MemoryCandidate floor = findFirstOfType(ordered, floorType);
            if (floor != null && !containsId(picked, floor.getId())) {
                MemoryRow row = toRow(floor, itemMax);
                picked.add(floor);
                rows.add(row);
                chars += row.getContent().length();
            }
        }

        for (MemoryCandidate candidate : ordered) {
            if (rows.size() >= topK) {
                break;
            }
            if (containsId(picked, candidate.getId()) || isDuplicate(candidate, picked)) {
                continue;
            }
            MemoryRow row = toRow(candidate, itemMax);
            if (chars + row.getContent().length() > maxChars) {
                continue;
            }
            picked.add(candidate);
            rows.add(row);
            chars += row.getContent().length();
        }
        return rows;
    }

    private static void addCandidates(Map<Long, MemoryCandidate> target, List<MemoryCandidate> rows) {
        if (rows == null) {
            return;
        }
        for (MemoryCandidate row : rows) {
            if (row != null && row.getId() != null && row.getContent() != null) {
                target.putIfAbsent(row.getId(), row);
            }
        }
    }

    private static MemoryCandidate findFirstOfType(List<MemoryCandidate> candidates, String type) {
        for (MemoryCandidate candidate : candidates) {
            if (type.equals(candidate.getMemoryType())) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean containsId(List<MemoryCandidate> candidates, Long id) {
        for (MemoryCandidate candidate : candidates) {
            if (candidate.getId() != null && candidate.getId().equals(id)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isDuplicate(MemoryCandidate candidate, List<MemoryCandidate> picked) {
        String key = normalizeKey(candidate.getContent());
        for (MemoryCandidate existing : picked) {
            if (key.equals(normalizeKey(existing.getContent()))) {
                return true;
            }
            if (candidate.getEmbedding() != null && existing.getEmbedding() != null
                    && EmbeddingCodec.cosine(candidate.getEmbedding(), existing.getEmbedding())
                    > NEAR_DUPLICATE_COSINE) {
                return true;
            }
        }
        return false;
    }

    private static MemoryRow toRow(MemoryCandidate candidate, int maxChars) {
        MemoryRow row = new MemoryRow();
        row.setId(candidate.getId());
        row.setMemoryType(candidate.getMemoryType());
        row.setTitle(candidate.getTitle());
        row.setContent(truncate(candidate.getContent(), maxChars));
        row.setImportance(candidate.getImportance());
        row.setOccurredAt(candidate.getOccurredAt());
        return row;
    }

    private static String normalizeKey(String content) {
        if (content == null) {
            return "";
        }
        return content.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max) + "...";
    }
}
