package com.example.javaweb.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.javaweb.entity.PushSubscription;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface PushSubscriptionMapper extends BaseMapper<PushSubscription> {

    @Select("SELECT * FROM push_subscription WHERE user_id = #{userId} ORDER BY id ASC")
    List<PushSubscription> selectByUser(@Param("userId") Long userId);

    @Select("SELECT * FROM push_subscription WHERE endpoint = #{endpoint} LIMIT 1")
    PushSubscription selectByEndpoint(@Param("endpoint") String endpoint);

    @Update("UPDATE push_subscription SET user_id = #{userId}, p256dh = #{p256dh}, " +
            "auth = #{auth}, user_agent = #{userAgent} WHERE endpoint = #{endpoint}")
    int updateByEndpoint(@Param("endpoint") String endpoint,
                         @Param("userId") Long userId,
                         @Param("p256dh") String p256dh,
                         @Param("auth") String auth,
                         @Param("userAgent") String userAgent);

    @Delete("DELETE FROM push_subscription WHERE endpoint = #{endpoint} AND user_id = #{userId}")
    int deleteByEndpoint(@Param("endpoint") String endpoint, @Param("userId") Long userId);

    @Update("UPDATE push_subscription SET last_used_at = NOW(3) WHERE id = #{id}")
    int touch(@Param("id") Long id);
}
