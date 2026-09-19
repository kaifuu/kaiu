package com.emergency.inspection.dto.query;

import com.emergency.inspection.common.PageQuery;
import com.emergency.inspection.entity.DeviceCommand;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/** 指令记录分页查询 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CommandQuery extends PageQuery {

    private String deviceSn;
    private String method;
    private DeviceCommand.Status status;

    /** 按下发时间过滤的日期区间(含端点) */
    private LocalDate startDate;
    private LocalDate endDate;
}
