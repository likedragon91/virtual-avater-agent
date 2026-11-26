package com.example.javaweb.agent.memory;

import com.example.javaweb.config.EmbeddingProperties;
import com.example.javaweb.dto.projection.MemoryCandidate;
import com.example.javaweb.mapper.MemoryMapper;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * 向量生成。未配置输入或模型 Bean 不存在时整体降级为 no-op，
 * 检索仍可只用 ngram 工作。
 */
@Service
public class EmbeddingService {
    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    @Resource
    private EmbeddingProperties properties;

    @Resource
    private MemoryMapper memoryMapper;

    @Resource(name = "summaryExecutor")
    private Executor summaryExecutor;

    @Resource
    private ObjectProvider<EmbeddingModel> embeddingModelProvider;

    public boolean isEnabled() {
        return properties.isEnabled() && embeddingModelProvider.getIfAvailable() != null;
    }

    public float[] embedOrNull(String text) {
        if (!isEnabled() || text == null || text.isBlank()) {
            return null;
        }
        try {
            EmbeddingModel model = embeddingModelProvider.getIfAvailable();
            return EmbeddingCodec.normalize(model.embed(text));
        } catch (Exception e) {
            log.warn("embedding failed: {}", e.getMessage());
            return null;
        }
    }

    /** 记忆写入后异步补向量，不阻塞工具调用 */
    public void embedAsync(Long memoryId, String text) {
        if (memoryId == null || !isEnabled()) {
            return;
        }
        summaryExecutor.execute(() -> {
            float[] vector = embedOrNull(text);
            if (vector == null) {
                return;
            }
            try {
                memoryMapper.updateEmbedding(memoryId, EmbeddingCodec.toBytes(vector),
                        properties.getModel(), vector.length, LocalDateTime.now());
            } catch (Exception e) {
                log.warn("save embedding failed, memoryId={}", memoryId, e);
            }
        });
    }

    /**
     * 补齐历史记忆的向量。开关关闭时直接返回，避免测试与未配置环境访问模型。
     */
    @Scheduled(fixedDelayString = "${embedding.backfill-delay-ms:60000}", initialDelay = 45000)
    public void backfillMissing() {
        if (!isEnabled()) {
            return;
        }
        try {
            List<MemoryCandidate> rows = memoryMapper.listMissingEmbedding(
                    Math.max(properties.getBatchSize(), 1));
            for (MemoryCandidate row : rows) {
                float[] vector = embedOrNull(row.getContent());
                if (vector == null) {
                    continue;
                }
                memoryMapper.updateEmbedding(row.getId(), EmbeddingCodec.toBytes(vector),
                        properties.getModel(), vector.length, LocalDateTime.now());
            }
        } catch (Exception e) {
            log.warn("embedding backfill failed", e);
        }
    }
}
