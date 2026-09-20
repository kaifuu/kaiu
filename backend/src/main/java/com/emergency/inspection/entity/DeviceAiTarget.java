package com.emergency.inspection.entity;

import com.baomidou.mybatisplus.annotation.IEnum;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** AI 目标识别记录:设备识别事件流(人员 / 车辆 / 船只) */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("device_ai_target")
public class DeviceAiTarget extends BaseEntity {

    public enum TargetType implements IEnum<String> {
        PERSON,   // 人员
        CAR,      // 车辆
        BOAT;     // 船只

        @Override
        public String getValue() {
            return name();
        }
    }

    private String deviceSn;

    private TargetType targetType;

    /** 识别置信度 % */
    private Integer confidence;

    private BigDecimal longitude;

    private BigDecimal latitude;

    private LocalDateTime eventTime;
}
