package com.emergency.inspection.dto.query;

import com.emergency.inspection.common.PageQuery;
import com.emergency.inspection.entity.SysLog;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 日志分页查询:keyword 模糊账号/动作/详情 */
@Data
@EqualsAndHashCode(callSuper = true)
public class LogQuery extends PageQuery {

    private SysLog.Type type;
    private Boolean success;
}
