package com.emergency.inspection.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 设备轨迹点:飞行器 OSD 每帧追加,航迹回放数据源 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("device_track_point")
public class DeviceTrackPoint extends BaseEntity {

    private String deviceSn;

    private LocalDateTime ts;

    private BigDecimal longitude;

    private BigDecimal latitude;

    /** 相对起飞点高度(米) */
    private BigDecimal height;

    private BigDecimal speed;

    private Integer battery;

    /** 航向(度) */
    private BigDecimal heading;
}
