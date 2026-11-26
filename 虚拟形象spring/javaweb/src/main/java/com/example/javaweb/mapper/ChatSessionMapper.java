package com.example.javaweb.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.javaweb.entity.ChatSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSession> {

    /**
     * 原子更新最后活跃时间与消息数，避免并发覆盖。
     */
    @Update("UPDATE chat_session SET last_active_at = CURRENT_TIMESTAMP(3), " +
            "message_count = message_count + #{delta} " +
            "WHERE id = #{sessionId} AND user_id = #{userId}")
    int touch(@Param("sessionId") Long sessionId,
              @Param("userId") Long userId,
              @Param("delta") int delta);

    /**
     * 只更新摘要相关列，避免整行回写覆盖并发修改。
     */
    @Update("UPDATE chat_session SET summary = #{summary}, " +
            "summary_message_id = #{summaryMessageId}, summary_updated_at = #{updatedAt} " +
            "WHERE id = #{sessionId}")
    int updateSummary(@Param("sessionId") Long sessionId,
                      @Param("summary") String summary,
                      @Param("summaryMessageId") Long summaryMessageId,
                      @Param("updatedAt") LocalDateTime updatedAt);
}
