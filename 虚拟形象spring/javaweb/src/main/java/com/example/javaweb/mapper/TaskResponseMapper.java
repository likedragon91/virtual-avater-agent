package com.example.javaweb.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.javaweb.entity.TaskResponseRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface TaskResponseMapper extends BaseMapper<TaskResponseRecord> {

    @Select("SELECT * FROM task_response WHERE occurrence_id = #{occurrenceId} LIMIT 1")
    TaskResponseRecord selectByOccurrence(@Param("occurrenceId") Long occurrenceId);

    @Select("SELECT * FROM task_response WHERE idempotency_key = #{key} LIMIT 1")
    TaskResponseRecord selectByIdempotencyKey(@Param("key") String key);

    @Select("SELECT COUNT(*) FROM task_response WHERE task_id = #{taskId}")
    long countByTaskId(@Param("taskId") Long taskId);

    @Select("SELECT DISTINCT DATE(responded_at) AS d FROM task_response " +
            "WHERE user_id = #{userId} AND responded_at >= #{from} ORDER BY d DESC LIMIT 60")
    List<LocalDate> selectResponseDates(@Param("userId") Long userId,
                                        @Param("from") LocalDateTime from);

    @Select("SELECT COALESCE(SUM(intimacy_delta), 0) FROM task_response " +
            "WHERE user_id = #{userId} AND DATE(responded_at) = #{date}")
    BigDecimal sumDailyDelta(@Param("userId") Long userId,
                             @Param("date") LocalDate date);

    @Select("SELECT COUNT(*) FROM task_response " +
            "WHERE user_id = #{userId} AND DATE(responded_at) = #{date}")
    long countDailyResponses(@Param("userId") Long userId,
                             @Param("date") LocalDate date);

    @Select("SELECT COALESCE(AVG(completion_ratio), 0) FROM task_response " +
            "WHERE user_id = #{userId} AND DATE(responded_at) = #{date}")
    BigDecimal avgDailyCompletion(@Param("userId") Long userId,
                                  @Param("date") LocalDate date);
}
