package com.emergency.inspection.dto;

import lombok.Data;

/** 登录入参 */
@Data
public class LoginRequest {

    private String username;
    private String password;

    /** 验证码 id(/auth/captcha 返回) */
    private String cid;

    /** 用户输入的验证码 */
    private String captcha;
}
