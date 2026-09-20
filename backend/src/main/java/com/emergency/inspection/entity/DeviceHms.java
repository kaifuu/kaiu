package com.emergency.inspection.entity;

import com.baomidou.mybatisplus.annotation.IEnum;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 机场健康告警:hms 事件中 hms_list 的逐条结构化落库。
 * 与 device_event 里的原始报文互补 —— 这里的行可直接按等级统计与筛选。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("device_hms")
public class DeviceHms extends BaseEntity {

    public enum Level implements IEnum<String> {
        NOTICE,     // 提示
        WARN,       // 警告
        ERROR;      // 严重

        @Override
        public String getValue() {
            return name();
        }

        /** 上云 API 的数字等级:0 提示 / 1 警告 / 2 严重 */
        public static Level of(Integer value) {
            return value == null || value == 0 ? NOTICE : value == 1 ? WARN : ERROR;
        }
    }

    private String deviceSn;

    /** 告警码,如 dock_cover_exception */
    private String code;

    private Level level;

    /** 告警归属模块索引 */
    private Integer moduleIndex;

    private String message;

    /** 告警发生时刻(设备侧时间) */
    private LocalDateTime eventTime;
}
