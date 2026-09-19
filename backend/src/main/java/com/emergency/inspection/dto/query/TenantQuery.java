package com.emergency.inspection.dto.query;

import com.emergency.inspection.common.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 租户分页查询:keyword 模糊名称/编码,enabled 精确 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TenantQuery extends PageQuery {

    private Boolean enabled;
}
