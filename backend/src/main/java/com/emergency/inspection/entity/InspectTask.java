package com.emergency.inspection.entity;

import com.baomidou.mybatisplus.annotation.IEnum;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 巡检任务:计划下发到具体点位 + 执行人的一次执行单 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("inspect_task")
public class InspectTask extends BaseEntity {

    public enum Status implements IEnum<String> {
        PENDING, RUNNING, DONE, OVERDUE, CANCELED;

        @Override
        public String getValue() {
            return name();
        }
    }

    /** 巡检结论;未完成为 null */
    public enum Result implements IEnum<String> {
        NORMAL, ABNORMAL;

        @Override
        public String getValue() {
            return name();
        }
    }

    private String name;

    private Long planId;

    private Long pointId;

    /** 点位名称冗余:点位改名/删除后任务仍可读 */
    private String pointName;

    private String executor;

    private String executorPhone;

    private LocalDateTime planStart;

    private LocalDateTime planEnd;

    private LocalDateTime actualStart;

    private LocalDateTime actualEnd;

    private Status status = Status.PENDING;

    private Result result;

    private String remark;
}
