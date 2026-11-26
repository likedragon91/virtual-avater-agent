package com.example.javaweb.mapper;

import com.example.javaweb.dto.projection.NotificationSettingsRow;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalTime;

@Mapper
public interface UserProfileMapper {

    @Select("SELECT preferences FROM user_profile WHERE user_id = #{userId}")
    String selectPreferences(@Param("userId") Long userId);

    @Insert("INSERT INTO user_profile (user_id, preferences) VALUES (#{userId}, #{preferences})")
    int insert(@Param("userId") Long userId, @Param("preferences") String preferences);

    @Update("UPDATE user_profile SET preferences = #{preferences} WHERE user_id = #{userId}")
    int updatePreferences(@Param("userId") Long userId, @Param("preferences") String preferences);

    @Select("SELECT dnd_start AS dndStart, dnd_end AS dndEnd, proactive_enabled AS proactiveEnabled " +
            "FROM user_profile WHERE user_id = #{userId}")
    NotificationSettingsRow selectNotificationSettings(@Param("userId") Long userId);

    @Select("SELECT ai_avatar FROM user_profile WHERE user_id = #{userId}")
    String selectAiAvatar(@Param("userId") Long userId);

    @Insert("INSERT INTO user_profile (user_id, ai_avatar) VALUES (#{userId}, #{aiAvatar}) " +
            "ON DUPLICATE KEY UPDATE ai_avatar = VALUES(ai_avatar)")
    int upsertAiAvatar(@Param("userId") Long userId, @Param("aiAvatar") String aiAvatar);

    @Update("UPDATE user_profile SET dnd_start = #{dndStart}, dnd_end = #{dndEnd}, " +
            "proactive_enabled = #{proactiveEnabled} WHERE user_id = #{userId}")
    int updateNotificationSettings(@Param("userId") Long userId,
                                   @Param("dndStart") LocalTime dndStart,
                                   @Param("dndEnd") LocalTime dndEnd,
                                   @Param("proactiveEnabled") Boolean proactiveEnabled);
}
