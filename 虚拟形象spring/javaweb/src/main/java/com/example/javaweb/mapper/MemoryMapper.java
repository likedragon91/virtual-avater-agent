package com.example.javaweb.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.javaweb.dto.projection.MemoryRow;
import com.example.javaweb.entity.MemoryItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
}
