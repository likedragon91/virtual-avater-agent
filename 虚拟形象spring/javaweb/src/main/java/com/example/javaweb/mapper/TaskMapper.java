package com.example.javaweb.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.javaweb.entity.Task;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TaskMapper extends BaseMapper<Task> {

    /**
     * 任务列表，附带下一次待处理 occurrence。
     */
    @Select("""
            <script>
            SELECT t.*,
              (SELECT o.id FROM task_occurrence o
                 WHERE o.task_id = t.id AND o.status IN ('PENDING','RINGING','MISSED')
                 ORDER BY o.occur_at ASC LIMIT 1) AS next_occurrence_id,
              (SELECT o.occur_at FROM task_occurrence o
                 WHERE o.task_id = t.id AND o.status IN ('PENDING','RINGING','MISSED')
                 ORDER BY o.occur_at ASC LIMIT 1) AS next_occurrence_at,
              (SELECT o.status FROM task_occurrence o
                 WHERE o.task_id = t.id AND o.status IN ('PENDING','RINGING','MISSED')
                 ORDER BY o.occur_at ASC LIMIT 1) AS next_occurrence_status
            FROM task t
            WHERE t.user_id = #{userId}
            <if test="status != null and status != ''"> AND t.status = #{status} </if>
            ORDER BY t.due_at ASC
            LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    List<Task> selectTaskList(@Param("userId") Long userId,
                              @Param("status") String status,
                              @Param("offset") long offset,
                              @Param("size") long size);
}
