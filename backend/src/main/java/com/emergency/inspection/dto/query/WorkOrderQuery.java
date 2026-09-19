package com.emergency.inspection.dto.query;

import com.emergency.inspection.common.PageQuery;
import com.emergency.inspection.entity.WorkOrder;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 工单分页查询:keyword 模糊标题/地点/处理人 */
@Data
@EqualsAndHashCode(callSuper = true)
public class WorkOrderQuery extends PageQuery {

    private WorkOrder.Status status;
    private WorkOrder.Priority priority;
    private String dept;
    private String handler;
}
