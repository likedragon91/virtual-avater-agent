package com.example.javaweb.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDate;

@Mapper
public interface DailyIntimacyMapper {

    @Insert("""
            INSERT INTO daily_intimacy (user_id, stat_date, gained, task_count, avg_completion, streak_days)
            VALUES (#{userId}, #{date}, #{gained}, #{taskCount}, #{avgCompletion}, #{streakDays})
            ON DUPLICATE KEY UPDATE
              gained = #{gained},
              task_count = #{taskCount},
              avg_completion = #{avgCompletion},
              streak_days = #{streakDays}
            """)
    int upsert(@Param("userId") Long userId,
               @Param("date") LocalDate date,
               @Param("gained") BigDecimal gained,
               @Param("taskCount") int taskCount,
               @Param("avgCompletion") BigDecimal avgCompletion,
               @Param("streakDays") int streakDays);
}
