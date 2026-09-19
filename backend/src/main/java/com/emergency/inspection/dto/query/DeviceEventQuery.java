package com.emergency.inspection.dto.query;

import com.emergency.inspection.common.PageQuery;
import com.emergency.inspection.entity.DeviceEvent;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 设备事件分页查询:keyword 模糊说明/方法名 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DeviceEventQuery extends PageQuery {

    private String deviceSn;
    private DeviceEvent.EventType eventType;
    private DeviceEvent.Level level;
    private String method;
}
