package com.emergency.inspection.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 角色:menuIdsJson 为授权菜单 id 数组;code=ADMIN 特判拥有全部菜单 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_role")
public class SysRole extends BaseEntity {

    public static final String CODE_ADMIN = "ADMIN";

    private String name;

    /** 角色编码,全局唯一 */
    private String code;

    private String remark;

    /** 授权菜单 id 数组 JSON,如 [1,2,3] */
    private String menuIdsJson = "[]";

    private Boolean enabled = true;
}
