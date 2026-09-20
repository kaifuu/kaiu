package com.emergency.inspection.entity;

import com.baomidou.mybatisplus.annotation.IEnum;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 固件升级任务:云端 ota_create 下发到设备,ota_progress 事件驱动进度 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("firmware_task")
public class FirmwareTask extends BaseEntity {

    public enum Status implements IEnum<String> {
        SENT,           // 已下发
        DOWNLOADING,    // 下载中
        UPGRADING,      // 升级中
        SUCCESS,        // 升级成功
        FAILED;         // 升级失败

        @Override
        public String getValue() {
            return name();
        }
    }

    private Long firmwareId;

    private String firmwareVersion;

    private String deviceSn;

    private String deviceName;

    private Status status;

    private Integer progress;

    private String message;

    private LocalDateTime finishedAt;
}
