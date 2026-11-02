package com.example.javaweb.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.javaweb.entity.user;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<user> {


}
