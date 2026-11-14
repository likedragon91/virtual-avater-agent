package com.example.javaweb.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CaptchaResponseDTO {
    //验证码唯一标识，登录时原样回传
    private String captchaId;
    //PNG 图片的 Base64 Data URI，可直接用于 <img src>
    private String captchaImage;
}
