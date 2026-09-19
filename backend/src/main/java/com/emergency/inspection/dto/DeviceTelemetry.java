package com.emergency.inspection.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 结构化遥测:把 device_osd 里那条原始 osd 报文拆成前端可直接渲染的字段。
 *
 * 上云 API 的 osd 字段随机型差异很大(机场有舱盖/推杆/环境,飞行器有姿态/定位/负载),
 * 若让前端各自解析 JSON,机型一多就会散落一堆容错代码。统一在后端归一化,
 * 前端按设备类型取对应区块即可。未识别的字段仍通过 raw 透出,不丢信息。
 */
@Data
public class DeviceTelemetry {

    private String deviceSn;
    private String deviceType;
    private String status;

    /** 工作状态码及其中文名(含义随机型不同) */
    private Integer modeCode;
    private String modeLabel;

    private LocalDateTime updateTime;

    /** 电量:飞行器取 battery,机场取 charging_state */
    private Integer batteryPercent;

    /* ---------------- 机场 ---------------- */

    private Integer coverState;
    private Integer putterState;
    /** 飞行器是否在舱:0 舱外 / 1 舱内 */
    private Integer droneInDock;
    private Double environmentTemperature;
    private Double windSpeed;
    private Double rainfall;
    private Integer mediaFileCount;
    private Map<String, Object> chargingState;
    private Map<String, Object> networkState;
    private Map<String, Object> subDevice;

    /* ---------------- 飞行器 ---------------- */

    private BigDecimal longitude;
    private BigDecimal latitude;
    /** 相对起飞点高度 m */
    private BigDecimal height;
    /** 海拔高度 m */
    private BigDecimal elevation;
    private Double attitudeHead;
    private Double horizontalSpeed;
    private Double verticalSpeed;
    private Integer gear;
    private Map<String, Object> positionState;
    private Map<String, Object> storage;
    private Map<String, Object> gimbal;
    private Map<String, Object> battery;

    /** 原始报文,前端如需展示未归一化的字段可直接取用 */
    private JsonNode raw;
}
