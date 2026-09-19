package com.emergency.inspection.dto;

import com.emergency.inspection.entity.SysUser;
import lombok.Data;

/** 人员新增/修改入参:角色用 roleId 平铺,避免前端传嵌套对象 */
@Data
public class SysUserForm {

    private String username;
    private String nickname;
    private String phone;
    private Long roleId;
    private Long orgId;
    private Long tenantId;
    private SysUser.Status status;

    /** 兼容前端开关:true → ENABLED */
    public SysUser.Status resolveStatus() {
        return status == null ? SysUser.Status.ENABLED : status;
    }
}
