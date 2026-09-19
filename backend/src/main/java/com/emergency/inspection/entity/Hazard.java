package com.emergency.inspection.entity;

import com.baomidou.mybatisplus.annotation.IEnum;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 隐患上报:巡检中发现的问题,走「待处理 → 处理中 → 已整改 → 已关闭」流程 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("hazard")
public class Hazard extends BaseEntity {

    /** 隐患等级 */
    public enum Level implements IEnum<String> {
        GENERAL, MAJOR, SEVERE, CRITICAL;

        @Override
        public String getValue() {
            return name();
        }
    }

    public enum Status implements IEnum<String> {
        PENDING, PROCESSING, RECTIFIED, CLOSED;

        @Override
        public String getValue() {
            return name();
        }
    }

    private String title;

    private Long pointId;

    private String pointName;

    /** 关联巡检任务 id */
    private Long taskId;

    private Level level = Level.GENERAL;

    private String description;

    /** 现场图片 URL 数组 JSON */
    private String imagesJson = "[]";

    private String reporter;

    private LocalDateTime reportTime = LocalDateTime.now();

    private Status status = Status.PENDING;

    private String handler;

    private String handleResult;

    private LocalDateTime handleTime;

    /** 整改期限 */
    private LocalDateTime deadline;
}
