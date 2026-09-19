package com.emergency.inspection.dto.query;

import com.emergency.inspection.common.PageQuery;
import com.emergency.inspection.entity.EmergencyEvent;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 应急事件分页查询:keyword 模糊标题/地点/上报人 */
@Data
@EqualsAndHashCode(callSuper = true)
public class EventQuery extends PageQuery {

    private EmergencyEvent.Category category;
    private EmergencyEvent.Level level;
    private EmergencyEvent.Status status;
}
