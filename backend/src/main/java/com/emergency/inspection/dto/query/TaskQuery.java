package com.emergency.inspection.dto.query;

import com.emergency.inspection.common.PageQuery;
import com.emergency.inspection.entity.InspectTask;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/** 巡检任务分页查询:keyword 模糊任务名/点位名/执行人 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TaskQuery extends PageQuery {

    private Long planId;
    private Long pointId;
    private InspectTask.Status status;
    private InspectTask.Result result;
    private String executor;

    /** 按计划开始时间过滤的日期区间(含端点) */
    private LocalDate startDate;
    private LocalDate endDate;
}
