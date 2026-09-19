package com.emergency.inspection.dto.query;

import com.emergency.inspection.common.PageQuery;
import com.emergency.inspection.entity.InspectDemand;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 需求分页查询:keyword 模糊名称/来源部门 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DemandQuery extends PageQuery {

    private InspectDemand.Status status;
    private String sourceDept;
    private String category;
}
