package com.emergency.inspection.dto.query;

import com.emergency.inspection.common.PageQuery;
import com.emergency.inspection.entity.AiAlgorithm;
import com.emergency.inspection.entity.AiAlgorithmAlarm;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 算法告警分页查询 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AlgoAlarmQuery extends PageQuery {

    private AiAlgorithm.Code algorithmCode;

    private AiAlgorithmAlarm.Level level;

    private AiAlgorithmAlarm.Status status;

    /** 发生时刻起 */
    private LocalDateTime startTime;

    /** 发生时刻止 */
    private LocalDateTime endTime;
}
