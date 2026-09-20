package com.emergency.inspection.dto.query;

import com.emergency.inspection.common.PageQuery;
import com.emergency.inspection.entity.DeviceHms;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** HMS 健康告警分页查询 */
@Data
@EqualsAndHashCode(callSuper = true)
public class HmsQuery extends PageQuery {

    private String deviceSn;

    private DeviceHms.Level level;
}
