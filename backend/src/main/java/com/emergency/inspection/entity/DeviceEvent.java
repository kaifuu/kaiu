package com.emergency.inspection.entity;

import com.baomidou.mybatisplus.annotation.IEnum;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 设备事件流:上下线、HMS 健康告警、任务进度、媒体上传 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("device_event")
public class DeviceEvent extends BaseEntity {

    public enum EventType implements IEnum<String> {
        ONLINE,        // 设备上线
        OFFLINE,       // 设备离线
        HMS,           // 健康告警
        FLIGHTTASK,    // 航线任务进度
        FILE_UPLOAD,   // 媒体文件上传
        OTHER;

        @Override
        public String getValue() {
            return name();
        }
    }

    public enum Level implements IEnum<String> {
        INFO, WARN, ERROR;

        @Override
        public String getValue() {
            return name();
        }
    }

    private String deviceSn;

    private EventType eventType;

    /** 设备上报的 method 原文,如 hms / flighttask_progress / file_upload_callback */
    private String method;

    private Level level = Level.INFO;

    private String message;

    private String dataJson = "{}";
}
