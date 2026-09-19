package com.emergency.inspection.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IEnum;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 系统用户(人员管理) */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class SysUser extends BaseEntity {

    public static final String USERNAME_ADMIN = "admin";

    /** 账号状态 */
    public enum Status implements IEnum<String> {
        ENABLED, DISABLED;

        @Override
        public String getValue() {
            return name();
        }
    }

    private String username;

    @JsonIgnore
    private String password;          // BCrypt 密文,永不外发

    private String nickname;

    private String phone;

    private Long roleId;

    private Long orgId;

    private Long tenantId;

    private Status status = Status.ENABLED;

    private LocalDateTime lastLoginAt;

    // ---------- 以下为联表展示字段,不落库 ----------

    @TableField(exist = false)
    private String roleName;

    @TableField(exist = false)
    private String roleCode;

    @TableField(exist = false)
    private String orgName;

    @TableField(exist = false)
    private String tenantName;
}
