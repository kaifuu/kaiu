package com.emergency.inspection.dto.query;

import com.emergency.inspection.common.PageQuery;
import com.emergency.inspection.entity.SafeAlert;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 安全预警分页查询 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SafeAlertQuery extends PageQuery {

    private SafeAlert.AlertType alertType;

    private SafeAlert.Level level;

    private SafeAlert.Status status;

    private String keyword;
}
