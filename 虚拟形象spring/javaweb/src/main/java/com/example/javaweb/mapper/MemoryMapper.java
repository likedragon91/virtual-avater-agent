package com.example.javaweb.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.javaweb.dto.projection.MemoryCandidate;
import com.example.javaweb.dto.projection.MemoryRow;
import com.example.javaweb.entity.MemoryItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface MemoryMapper extends BaseMapper<MemoryItem> {

    /**
     * 优先走 ngram 全文索引；查询词过短或没有命中时，由 Service 回退到 searchLike。
     */
    @Select("""
            <script>
            SELECT id, memory_type, title, content, importance, occurred_at
            FROM memory_item
            WHERE user_id = #{userId} AND status = 'ACTIVE'
              AND (expires_at IS NULL OR expires_at > NOW(3))
            <if test="type != null and type != ''"> AND memory_type = #{type} </if>
            <if test="query != null and query != ''">
              AND MATCH(content) AGAINST (#{query} IN NATURAL LANGUAGE MODE)
            </if>
            ORDER BY importance DESC, COALESCE(occurred_at, created_at) DESC
            LIMIT #{limit}
            </script>
            """)
    List<MemoryRow> search(@Param("userId") Long userId,
                           @Param("query") String query,
                           @Param("type") String type,
                           @Param("limit") int limit);

    /**
     * 全文索引的兜底方案，适合单字/短词查询。
     */
    @Select("""
            <script>
            SELECT id, memory_type, title, content, importance, occurred_at
            FROM memory_item
            WHERE user_id = #{userId} AND status = 'ACTIVE'
              AND (expires_at IS NULL OR expires_at > NOW(3))
            <if test="type != null and type != ''"> AND memory_type = #{type} </if>
            <if test="query != null and query != ''">
              AND content LIKE CONCAT('%', #{query}, '%')
            </if>
            ORDER BY importance DESC, COALESCE(occurred_at, created_at) DESC
            LIMIT #{limit}
            </script>
            """)
    List<MemoryRow> searchLike(@Param("userId") Long userId,
                               @Param("query") String query,
                               @Param("type") String type,
                               @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM memory_item WHERE user_id = #{userId} AND status = 'ACTIVE'")
    long countActive(@Param("userId") Long userId);

    /**
     * 按当前消息做 ngram 召回，并带回相关度分数供重排。
     */
    @Select("""
            SELECT id, memory_type, title, content, importance, confidence, occurred_at, created_at, expires_at,
                   decay_score, embedding, embedding_model, embedding_dim,
                   MATCH(content) AGAINST (#{query} IN NATURAL LANGUAGE MODE) AS score
            FROM memory_item
            WHERE user_id = #{userId} AND status = 'ACTIVE'
              AND (expires_at IS NULL OR expires_at > NOW(3))
              AND MATCH(content) AGAINST (#{query} IN NATURAL LANGUAGE MODE)
            ORDER BY score DESC
            LIMIT #{limit}
            """)
    List<MemoryCandidate> searchScored(@Param("userId") Long userId,
                                       @Param("query") String query,
                                       @Param("limit") int limit);

    /**
     * 没有可用查询词时的候选池：按重要度与时间排序。
     */
    @Select("""
            SELECT id, memory_type, title, content, importance, confidence, occurred_at, created_at, expires_at,
                   decay_score, embedding, embedding_model, embedding_dim,
                   0 AS score
            FROM memory_item
            WHERE user_id = #{userId} AND status = 'ACTIVE'
              AND (expires_at IS NULL OR expires_at > NOW(3))
            ORDER BY importance DESC, COALESCE(occurred_at, created_at) DESC
            LIMIT #{limit}
            """)
    List<MemoryCandidate> searchRecent(@Param("userId") Long userId, @Param("limit") int limit);

    /**
     * 类型保底：保证画像/偏好至少各有一条进入候选。
     */
    @Select("""
            SELECT id, memory_type, title, content, importance, confidence, occurred_at, created_at, expires_at,
                   decay_score, embedding, embedding_model, embedding_dim,
                   0 AS score
            FROM memory_item
            WHERE user_id = #{userId} AND status = 'ACTIVE'
              AND (expires_at IS NULL OR expires_at > NOW(3))
              AND memory_type = #{type}
            ORDER BY importance DESC, COALESCE(occurred_at, created_at) DESC
            LIMIT #{limit}
            """)
    List<MemoryCandidate> selectFloorByType(@Param("userId") Long userId,
                                            @Param("type") String type,
                                            @Param("limit") int limit);

    /**
     * 尚未生成向量的记忆，供后台补齐任务使用。
     */
    @Select("""
            SELECT id, memory_type, title, content, importance, confidence, occurred_at, expires_at,
                   decay_score, embedding_model, embedding_dim, 0 AS score
            FROM memory_item
            WHERE status = 'ACTIVE' AND embedding IS NULL
            ORDER BY id ASC
            LIMIT #{limit}
            """)
    List<MemoryCandidate> listMissingEmbedding(@Param("limit") int limit);

    @Update("""
            UPDATE memory_item
            SET embedding = #{embedding}, embedding_model = #{model},
                embedding_dim = #{dim}, embedded_at = #{embeddedAt}
            WHERE id = #{id}
            """)
    int updateEmbedding(@Param("id") Long id,
                        @Param("embedding") byte[] embedding,
                        @Param("model") String model,
                        @Param("dim") Integer dim,
                        @Param("embeddedAt") LocalDateTime embeddedAt);
}
