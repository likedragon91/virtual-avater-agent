package com.example.javaweb.dto.response;

import lombok.Data;

import java.util.List;

//用户列表分页响应
@Data
public class UserListResponseDTO {
    //当前页数据
    private List<UserLoginResponseDTO.UserDetailResponseDTO> records;
    //总记录数
    private Long total;
    //当前页码
    private Long page;
    //每页条数
    private Long size;
}
