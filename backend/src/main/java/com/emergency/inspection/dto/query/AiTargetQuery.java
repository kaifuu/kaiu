package com.emergency.inspection.dto.query;

import com.emergency.inspection.common.PageQuery;
import com.emergency.inspection.entity.DeviceAiTarget;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** AI 识别记录分页查询 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AiTargetQuery extends PageQuery {

    private String deviceSn;

    private DeviceAiTarget.TargetType type;
}
