package com.emergency.inspection.dto.query;

import com.emergency.inspection.common.PageQuery;
import com.emergency.inspection.entity.FirmwareTask;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 固件升级任务分页查询 */
@Data
@EqualsAndHashCode(callSuper = true)
public class FirmwareTaskQuery extends PageQuery {

    private String deviceSn;

    private FirmwareTask.Status status;
}
