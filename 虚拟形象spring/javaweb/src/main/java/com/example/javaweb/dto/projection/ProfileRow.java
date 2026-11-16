package com.example.javaweb.dto.projection;

import lombok.Data;

import java.math.BigDecimal;

/**
 * get_user_profile 工具的查询结果。只包含可展示字段，不含手机号/邮箱/身份证。
 */
@Data
public class ProfileRow {
    private String nickname;
    private Integer userType;
    private BigDecimal intimacyTotal;
    private String stageCode;
    private String userCapStage;
}
