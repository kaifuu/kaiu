package com.emergency.inspection.dto.query;

import com.emergency.inspection.common.PageQuery;
import com.emergency.inspection.entity.WaylineJob;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 航线任务分页查询 */
@Data
@EqualsAndHashCode(callSuper = true)
public class WaylineJobQuery extends PageQuery {

    private String dockSn;

    private WaylineJob.Status status;
}
