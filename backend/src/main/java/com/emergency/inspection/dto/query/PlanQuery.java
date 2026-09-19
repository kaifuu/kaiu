package com.emergency.inspection.dto.query;

import com.emergency.inspection.common.PageQuery;
import com.emergency.inspection.entity.InspectPlan;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 巡检计划分页查询:keyword 模糊名称/编码/负责人 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PlanQuery extends PageQuery {

    private InspectPlan.Category category;
    private InspectPlan.Status status;
    private String owner;
}
