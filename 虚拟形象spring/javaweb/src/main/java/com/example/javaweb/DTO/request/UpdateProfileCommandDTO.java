package com.example.javaweb.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户自助编辑资料。只允许修改展示字段，不能改 userType/status/username/email/password。
 */
@Data
public class UpdateProfileCommandDTO {
    @Size(max = 50, message = "昵称不能超过 50 个字符")
    private String nickname;

    @Size(max = 255, message = "头像地址不能超过 255 个字符")
    private String avatar;

    @Pattern(regexp = "^$|^1[3456789]\\d{9}$", message = "手机号格式错误")
    private String phone;

    @Min(value = 0, message = "性别只能是 0、1、2")
    @Max(value = 2, message = "性别只能是 0、1、2")
    private Integer gender;

    @Pattern(regexp = "^$|^\\d{4}-\\d{2}-\\d{2}$", message = "生日格式应为 yyyy-MM-dd")
    private String birthday;
}
