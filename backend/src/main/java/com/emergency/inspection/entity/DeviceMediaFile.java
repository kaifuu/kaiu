package com.emergency.inspection.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 媒体文件:航线任务执行完成后,设备向云端要存储配置(storage_config_get 请求),
 * 上传后逐个发 file_upload_callback 事件 —— 本表即该事件的落库形态。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("device_media_file")
public class DeviceMediaFile extends BaseEntity {

    private String deviceSn;

    /** 归属航线任务号(与 wayline_job.flight_id 对应) */
    private String flightId;

    /** 对象存储键:{prefix}/{flight_id}/{序号}_{时间戳}.{ext} */
    private String objectKey;

    private String path;

    private String name;

    /** 0 原图 / 1 低空图 / 2 缩略图…(上云 API sub_file_type) */
    private Integer subFileType;

    /** true 原始媒体 / false 缩略图或预览 */
    private Boolean isOriginal;

    private String droneModelKey;

    private String payloadModelKey;

    private BigDecimal longitude;

    private BigDecimal latitude;

    private BigDecimal absoluteAltitude;

    private BigDecimal relativeAltitude;

    private BigDecimal gimbalYawDegree;

    /** 拍摄时刻(设备侧时间) */
    private LocalDateTime takenAt;

    // ---------- 展示字段,不落库 ----------

    /** 归属任务名(联查回填) */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String flightName;
}
