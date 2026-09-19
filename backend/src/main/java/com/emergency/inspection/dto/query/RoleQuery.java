package com.emergency.inspection.dto.query;

import com.emergency.inspection.common.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 角色分页查询:keyword 模糊名称/编码 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RoleQuery extends PageQuery {

    private Boolean enabled;
}
