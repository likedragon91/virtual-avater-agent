package com.example.javaweb.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.javaweb.dto.projection.OccurrenceRow;
import com.example.javaweb.entity.TaskOccurrence;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface TaskOccurrenceMapper extends BaseMapper<TaskOccurrence> {

    @Select("""
            <script>
            SELECT o.id, o.task_id, o.user_id, o.occur_at, o.expires_at, o.status,
                   o.fired_at, o.responded_at,
                   t.title, t.note, t.priority, t.response_window_minutes,
                   r.id AS response_id, r.completion_ratio,
                   r.note AS response_note, r.intimacy_delta, r.revised_at
            FROM task_occurrence o
            JOIN task t ON t.id = o.task_id
            LEFT JOIN task_response r ON r.occurrence_id = o.id
            WHERE o.user_id = #{userId}
            <if test="from != null"> AND o.occur_at &gt;= #{from} </if>
            <if test="to != null"> AND o.occur_at &lt;= #{to} </if>
            <if test="statuses != null and statuses.size() > 0">
              AND o.status IN
              <foreach collection="statuses" item="s" open="(" separator="," close=")">#{s}</foreach>
            </if>
            ORDER BY o.occur_at ASC
            LIMIT #{limit}
            </script>
            """)
    List<OccurrenceRow> queryOccurrences(@Param("userId") Long userId,
                                         @Param("from") LocalDateTime from,
                                         @Param("to") LocalDateTime to,
                                         @Param("statuses") List<String> statuses,
                                         @Param("limit") int limit);

    @Select("SELECT * FROM task_occurrence WHERE task_id = #{taskId} " +
            "AND status IN ('PENDING','RINGING','MISSED') ORDER BY occur_at ASC LIMIT 1")
    TaskOccurrence selectNextOccurrence(@Param("taskId") Long taskId);

    @Select("SELECT * FROM task_occurrence " +
            "WHERE status = 'PENDING' AND occur_at <= NOW(3) AND expires_at > NOW(3) " +
            "ORDER BY occur_at ASC LIMIT #{limit}")
    List<TaskOccurrence> selectDuePending(@Param("limit") int limit);

    @Update("UPDATE task_occurrence SET status = 'RINGING', fired_at = NOW(3) " +
            "WHERE id = #{id} AND status = 'PENDING'")
    int markRingingById(@Param("id") Long id);

    @Update("UPDATE task_occurrence SET status = 'RINGING', fired_at = NOW(3) " +
            "WHERE status = 'PENDING' AND occur_at <= NOW(3) AND expires_at > NOW(3)")
    int markRinging();

    @Update("UPDATE task_occurrence SET status = 'MISSED' " +
            "WHERE status = 'RINGING' AND expires_at < NOW(3)")
    int markMissed();

    @Update("UPDATE task_occurrence SET status = 'MISSED' " +
            "WHERE status = 'PENDING' AND expires_at < NOW(3)")
    int markExpiredPending();

    @Delete("DELETE FROM task_occurrence WHERE task_id = #{taskId} AND user_id = #{userId} AND status = 'PENDING'")
    int deletePending(@Param("taskId") Long taskId, @Param("userId") Long userId);
}
