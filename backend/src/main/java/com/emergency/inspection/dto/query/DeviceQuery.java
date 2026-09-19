package com.emergency.inspection.dto.query;

import com.emergency.inspection.common.PageQuery;
import com.emergency.inspection.entity.Device;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 设备分页查询:keyword 模糊名称/序列号/机型 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DeviceQuery extends PageQuery {

    private Device.DeviceType deviceType;
    private Device.Status status;
    private String gatewaySn;
}
