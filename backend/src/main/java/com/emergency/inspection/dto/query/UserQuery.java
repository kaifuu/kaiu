package com.emergency.inspection.dto.query;

import com.emergency.inspection.common.PageQuery;
import com.emergency.inspection.entity.SysUser;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 人员分页查询:keyword 模糊账号/姓名/手机号 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserQuery extends PageQuery {

    private Long roleId;
    private Long orgId;
    private Long tenantId;
    private SysUser.Status status;
}
