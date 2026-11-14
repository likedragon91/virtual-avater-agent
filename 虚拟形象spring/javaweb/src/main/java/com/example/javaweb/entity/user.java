package com.example.javaweb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.javaweb.enums.UserStatus;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("user")
//用户实体类
public class User {
    //主键ID
    @TableId(type = IdType.AUTO)
    private Long id;

    //账号
    private String username;

    //邮箱
    private String email;

    //密码（后端加盐 SM3 后的哈希）
    private String password;

    //密码盐（每个用户独立，32 位十六进制）
    private String salt;

    //用户名（昵称）
    private String nickname;

    //头像
    private String avatar;

    //手机电话
    private String phone;

    //性别：0-未知，1-男，2-女
    private Integer gender;

    //性别名称
    @TableField("gender_display_name")
    private String genderDisplayName;

    //身份证号
    @TableField("id_card")
    private String idCard;

    //生日
    private LocalDate birthday;

    //用户类型：1-普通用户，2-管理员，3-VIP
    @TableField("user_type")
    private Integer userType;

    //用户类型名称
    @TableField("user_type_display_name")
    private String userTypeDisplayName;

    //状态：0-禁用，1-正常，2-锁定
    private Integer status;
    //是否激活
    public Boolean isActive(){
        return UserStatus.ENABLED.getCode().equals(this.status);
    }

    //状态名称
    @TableField("status_display_name")
    private String statusDisplayName;

    //创建时间
    @TableField("created_at")
    private LocalDateTime createdAt;

    //更新时间
    @TableField("updated_at")
    private LocalDateTime updatedAt;

}
