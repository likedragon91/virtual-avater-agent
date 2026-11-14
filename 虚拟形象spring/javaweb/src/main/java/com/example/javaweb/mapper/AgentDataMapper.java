package com.example.javaweb.mapper;

import com.example.javaweb.dto.projection.ProfileRow;
import com.example.javaweb.dto.projection.TaskRow;
import com.example.javaweb.dto.projection.TaskStatsRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Agent 工具专用的只读查询。所有方法都必须显式带 userId。
 */
@Mapper
public interface AgentDataMapper {

    @Select("""
            SELECT u.nickname, u.user_type, r.intimacy_total, r.stage_code, r.user_cap_stage
            FROM `user` u
            LEFT JOIN relationship_state r ON r.user_id = u.id
            WHERE u.id = #{userId}
            """)
    ProfileRow profile(@Param("userId") Long userId);

    @Select("""
            <script>
            SELECT t.id, t.title, t.note, t.due_at, t.tz, t.priority, t.status, t.rrule,
                   o.id AS occurrence_id, o.occur_at, o.status AS occurrence_status, o.expires_at
            FROM task t
            LEFT JOIN task_occurrence o ON o.task_id = t.id
            WHERE t.user_id = #{userId} AND t.status = 'ACTIVE'
            <if test="from != null"> AND t.due_at &gt;= #{from} </if>
            <if test="to != null"> AND t.due_at &lt;= #{to} </if>
            <if test="status != null and status != ''"> AND o.status = #{status} </if>
            ORDER BY t.due_at ASC
            LIMIT #{limit}
            </script>
            """)
    List<TaskRow> queryTasks(@Param("userId") Long userId,
                             @Param("from") LocalDateTime from,
                             @Param("to") LocalDateTime to,
                             @Param("status") String status,
                             @Param("limit") int limit);

    @Select("""
            <script>
            SELECT COUNT(*) AS total, AVG(completion_ratio) AS avg_completion,
                   SUM(CASE WHEN completion_ratio &gt;= 100 THEN 1 ELSE 0 END) AS completed
            FROM task_response
            WHERE user_id = #{userId}
            <if test="from != null"> AND responded_at &gt;= #{from} </if>
            <if test="to != null"> AND responded_at &lt;= #{to} </if>
            </script>
            """)
    TaskStatsRow taskStats(@Param("userId") Long userId,
                           @Param("from") LocalDateTime from,
                           @Param("to") LocalDateTime to);

    @Select("""
            <script>
            SELECT DISTINCT DATE(responded_at) AS d
            FROM task_response
            WHERE user_id = #{userId}
            <if test="from != null"> AND responded_at &gt;= #{from} </if>
            ORDER BY d DESC
            LIMIT 60
            </script>
            """)
    List<LocalDate> responseDates(@Param("userId") Long userId,
                                  @Param("from") LocalDateTime from);
}
