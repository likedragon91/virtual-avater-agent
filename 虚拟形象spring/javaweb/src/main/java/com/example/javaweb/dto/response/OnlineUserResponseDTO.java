package com.example.javaweb.dto.response;

import lombok.Data;

//在线用户响应
@Data
public class OnlineUserResponseDTO {
    //用户ID
    private Long userId;
    //账号
    private String username;
    //昵称
    private String nickname;
    //头像
    private String avatar;
    //用户类型：1-普通用户，2-管理员，3-VIP
    private Integer userType;
    //用户类型名称
    private String userTypeDisplayName;
    //登录时间
    private String loginTime;
    //最后活跃时间
    private String lastActiveTime;
}
