package com.emergency.inspection.dto;

import lombok.Data;

/** 通用处置表单:隐患整改与应急事件处置共用 */
@Data
public class HandleForm {

    /** 处理人 / 现场指挥 */
    private String operator;

    /** 处理结果 / 处置措施 */
    private String content;

    /** 目标状态(PENDING/PROCESSING/RECTIFIED/CLOSED 或 PENDING/RESPONDING/HANDLED/ARCHIVED) */
    private String status;
}
