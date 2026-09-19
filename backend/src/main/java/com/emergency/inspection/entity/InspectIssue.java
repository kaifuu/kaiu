package com.emergency.inspection.entity;

import com.baomidou.mybatisplus.annotation.IEnum;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 巡检问题:AI 算法识别或人工上报,是工单的来源 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("inspect_issue")
public class InspectIssue extends BaseEntity {

    /** 问题类型(对应大屏「算法识别类型」统计口径) */
    public enum IssueType implements IEnum<String> {
        ILLEGAL_BUILD,   // 疑似违建
        GARBAGE,         // 垃圾堆放
        FLOATING,        // 水面漂浮物
        ILLEGAL_NET,     // 非法围网
        OUTFALL,         // 疑似排污口
        SLUDGE,          // 渣土车
        CRACK,           // 路面裂纹
        WATER_PLANT,     // 水面植物
        CONSTRUCTION,    // 施工堆料
        OTHER;

        @Override
        public String getValue() {
            return name();
        }
    }

    public enum Source implements IEnum<String> {
        AI,      // 算法识别
        MANUAL;  // 人工上报

        @Override
        public String getValue() {
            return name();
        }
    }

    public enum Status implements IEnum<String> {
        PENDING,      // 待处理
        DISPATCHED,   // 已推送
        WORK_ORDER,   // 已生成工单
        CLOSED;       // 已结案

        @Override
        public String getValue() {
            return name();
        }
    }

    private String code;

    private String title;

    private IssueType issueType = IssueType.OTHER;

    private Long pointId;

    private String pointName;

    private String address;

    private BigDecimal longitude;

    private BigDecimal latitude;

    /** 发现该问题的设备 */
    private String deviceSn;

    private String deviceName;

    private Source source = Source.AI;

    /** 需求部门 */
    private String dept;

    private String description;

    /** 现场图片 URL 数组 JSON */
    private String imagesJson = "[]";

    private LocalDateTime foundAt = LocalDateTime.now();

    private Status status = Status.PENDING;
}
