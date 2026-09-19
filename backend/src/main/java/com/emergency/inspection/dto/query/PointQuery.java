package com.emergency.inspection.dto.query;

import com.emergency.inspection.common.PageQuery;
import com.emergency.inspection.entity.InspectPoint;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 巡检点分页查询:keyword 模糊名称/编码/地址 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PointQuery extends PageQuery {

    private InspectPoint.Category category;
    private InspectPoint.RiskLevel riskLevel;
    private InspectPoint.Status status;
    private String area;
}
