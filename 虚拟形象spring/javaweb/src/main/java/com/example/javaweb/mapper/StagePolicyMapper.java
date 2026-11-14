package com.example.javaweb.mapper;

import com.example.javaweb.entity.StagePolicy;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface StagePolicyMapper {

    @Select("SELECT code, version, config, active, updated_at " +
            "FROM stage_policy WHERE code = #{code} AND active = 1 LIMIT 1")
    StagePolicy selectActive(@Param("code") String code);
}
