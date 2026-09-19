package com.emergency.inspection.dto.query;

import com.emergency.inspection.common.PageQuery;
import com.emergency.inspection.entity.Hazard;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 隐患分页查询:keyword 模糊标题/点位名/上报人 */
@Data
@EqualsAndHashCode(callSuper = true)
public class HazardQuery extends PageQuery {

    private Hazard.Level level;
    private Hazard.Status status;
    private Long pointId;
    private Long taskId;
}
