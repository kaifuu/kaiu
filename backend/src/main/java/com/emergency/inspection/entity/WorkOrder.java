package com.emergency.inspection.entity;

import com.baomidou.mybatisplus.annotation.IEnum;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 工单:巡检问题派发到责任部门后的处置单 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("work_order")
public class WorkOrder extends BaseEntity {

    public enum Priority implements IEnum<String> {
        LOW, NORMAL, HIGH, URGENT;

        @Override
        public String getValue() {
            return name();
        }
    }

    public enum Status implements IEnum<String> {
        PENDING,      // 待派发
        PROCESSING,   // 处理中
        HANDLED,      // 已处理
        CLOSED;       // 已结案

        @Override
        public String getValue() {
            return name();
        }
    }

    private String code;

    private String title;

    /** 来源问题 */
    private Long issueId;

    private String issueTitle;

    /** 冗余问题类型,列表页无需回查问题表 */
    private String issueType;

    private String pointName;

    private String address;

    private BigDecimal longitude;

    private BigDecimal latitude;

    /** 需求部门 */
    private String dept;

    private String handler;

    private String handlerPhone;

    /** 处理部门 */
    private String handleDept;

    private Priority priority = Priority.NORMAL;

    private Status status = Status.PENDING;

    private String description;

    private String result;

    private LocalDateTime dispatchedAt;

    private LocalDateTime deadline;

    private LocalDateTime finishedAt;
}
