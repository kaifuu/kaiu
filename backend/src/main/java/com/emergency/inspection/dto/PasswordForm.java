package com.emergency.inspection.dto;

import lombok.Data;

/** 个人信息-修改密码 */
@Data
public class PasswordForm {

    private String oldPassword;
    private String newPassword;
}
