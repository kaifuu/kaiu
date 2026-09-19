package com.emergency.inspection.dto.query;

import com.emergency.inspection.common.PageQuery;
import com.emergency.inspection.entity.Pilot;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 飞手分页查询:keyword 模糊姓名/手机号/片区 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PilotQuery extends PageQuery {

    private Pilot.Status status;
    private Pilot.CertType certType;
    private String area;
}
