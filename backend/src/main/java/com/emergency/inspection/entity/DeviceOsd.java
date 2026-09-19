package com.emergency.inspection.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 设备最新遥测快照:每台设备一行,收到 osd 报文即覆盖更新。
 * 原始报文整体存 osd_json(机型间字段差异大,不做强约束),
 * 同时把跨机型通用的几个字段抽成列,便于列表页直接查询与排序。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("device_osd")
public class DeviceOsd extends BaseEntity {

    private String deviceSn;

    /** 原始 osd 报文 JSON */
    private String osdJson = "{}";

    /** 工作状态码;含义随机型不同,见 DjiModel 中的状态字典 */
    private Integer modeCode;

    private BigDecimal longitude;

    private BigDecimal latitude;

    /** 相对起飞点高度 m */
    private BigDecimal height;

    /** 电池电量 % */
    private Integer batteryPercent;
}
