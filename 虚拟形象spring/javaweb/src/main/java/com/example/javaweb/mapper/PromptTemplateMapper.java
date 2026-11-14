package com.example.javaweb.mapper;

import com.example.javaweb.entity.PromptTemplate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PromptTemplateMapper {

    @Select("SELECT code, version, content, active, updated_at " +
            "FROM prompt_template WHERE code = #{code} AND active = 1 " +
            "ORDER BY version DESC LIMIT 1")
    PromptTemplate selectActive(@Param("code") String code);
}
