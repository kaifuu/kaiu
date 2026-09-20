package com.emergency.inspection.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 机场直播能力:设备经 state 主题上报的 live_capacity 原文,
 * 里面是可用视频源清单(设备 → 相机 → 镜头),直播 TAB 据此拼 video_id。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("device_live_capacity")
public class DeviceLiveCapacity extends BaseEntity {

    private String deviceSn;

    /** live_capacity 原文 JSON */
    private String capacityJson;
}
