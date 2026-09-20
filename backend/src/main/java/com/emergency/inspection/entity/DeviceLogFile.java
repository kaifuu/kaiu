package com.emergency.inspection.entity;

import com.baomidou.mybatisplus.annotation.IEnum;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 远程日志文件:logs_file_list 拉取到的设备日志清单,logs_file_upload 触发上传 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("device_log_file")
public class DeviceLogFile extends BaseEntity {

    public enum Module implements IEnum<String> {
        DOCK,   // 机场本体日志
        DRONE;  // 挂载飞行器日志

        @Override
        public String getValue() {
            return name();
        }
    }

    public enum Status implements IEnum<String> {
        FOUND,      // 已发现,待上传
        UPLOADING,  // 上传中
        UPLOADED,   // 已上传到对象存储
        FAILED;     // 上传失败

        @Override
        public String getValue() {
            return name();
        }
    }

    private String deviceSn;

    /** 设备侧的日志文件标识 */
    private String fileId;

    private String name;

    private Module module;

    /** 字节 */
    private Long size;

    private LocalDateTime fileTime;

    private Status status;

    /** 上传进度 % */
    private Integer percent;

    /** 上传完成后的对象存储 key */
    private String objectKey;
}
