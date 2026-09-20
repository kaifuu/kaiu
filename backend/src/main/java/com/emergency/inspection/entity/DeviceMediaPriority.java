package com.emergency.inspection.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 当前优先上传媒体的任务号:设备以 highest_priority_upload_flighttask_media 事件告知,
 * 云端可经 upload_flighttask_media_prioritize 指令改写。每台机场一条。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("device_media_priority")
public class DeviceMediaPriority extends BaseEntity {

    private String deviceSn;

    private String flightId;
}
