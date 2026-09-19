package com.emergency.inspection.entity;

import com.baomidou.mybatisplus.annotation.IEnum;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 巡检需求:各业务部门提报的巡检申请 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("inspect_demand")
public class InspectDemand extends BaseEntity {

    public enum Status implements IEnum<String> {
        PENDING,    // 待执行
        EXECUTED,   // 已执行
        OVERDUE,    // 已超时
        CANCELED;   // 已取消

        @Override
        public String getValue() {
            return name();
        }
    }

    private String code;

    private String title;

    /** 需求来源部门 */
    private String sourceDept;

    /** 巡检类别,复用计划的分类口径 */
    private String category = "DAILY";

    private String description;

    /** 期望执行日期 */
    private LocalDate expectDate;

    private LocalDateTime submittedAt = LocalDateTime.now();

    private Status status = Status.PENDING;

    private String executor;

    private LocalDateTime executedAt;

    private String remark;
}
